import com.kgl.vulkan.Vk
import com.kgl.vulkan.enums.*
import com.kgl.vulkan.handles.*
import com.kgl.vulkan.utils.VkFlag
import com.kgl.vulkan.utils.contains
import com.kgl.vulkan.utils.or
import kotlinx.io.core.*
import sample.utils.BaseApplication
import sample.utils.readAllBytes

fun main(args: Array<String>) {
    TriangleApplication().use {
        it.mainLoop()
    }
}

const val RESOURCE_DIR = "."

data class Vec2(val x: Float, val y: Float)
data class Vec3(val x: Float, val y: Float, val z: Float)
data class Vertex(val pos: Vec2, val color: Vec3)

class TriangleApplication : BaseApplication() {
    private val vertShaderModule: ShaderModule
    private val fragShaderModule: ShaderModule
    private val pipelineLayout: PipelineLayout
    private val renderPass: RenderPass

    private val vertexBuffer: Buffer
    private val vertexBufferMemory: DeviceMemory
    private val indexBuffer: Buffer
    private val indexBufferMemory: DeviceMemory

    private lateinit var swapchainFramebuffers: List<Framebuffer>
    private lateinit var graphicsPipeline: Pipeline
    private lateinit var commandBuffers: List<CommandBuffer>

    private val vertices = arrayOf(
        Vertex(Vec2(-0.5f, -0.5f), Vec3(1.0f, 1.0f, 1.0f)),
        Vertex(Vec2(0.5f, -0.5f), Vec3(1.0f, 0.0f, 0.0f)),
        Vertex(Vec2(0.5f, 0.5f), Vec3(0.0f, 1.0f, 0.0f)),
        Vertex(Vec2(-0.5f, 0.5f), Vec3(0.0f, 0.0f, 1.0f))
    )
    private val indices = ushortArrayOf(
        0u, 1u, 2u,
        2u, 3u, 0u
    )

    init {
        val vertShaderCode = readAllBytes("$RESOURCE_DIR/shaders/shader.vert.spv")
        val fragShaderCode = readAllBytes("$RESOURCE_DIR/shaders/shader.frag.spv")

        vertShaderModule = device.createShaderModule(vertShaderCode.asUByteArray())
        fragShaderModule = device.createShaderModule(fragShaderCode.asUByteArray())

        pipelineLayout = device.createPipelineLayout()

        renderPass = device.createRenderPass {
            attachments {
                description {
                    format = surfaceFormat.format
                    samples = SampleCount.`1`
                    loadOp = AttachmentLoadOp.CLEAR
                    storeOp = AttachmentStoreOp.STORE

                    stencilLoadOp = AttachmentLoadOp.DONT_CARE
                    stencilStoreOp = AttachmentStoreOp.DONT_CARE

                    initialLayout = ImageLayout.UNDEFINED
                    finalLayout = ImageLayout.PRESENT_SRC_KHR
                }
            }
            subpasses {
                description {
                    pipelineBindPoint = PipelineBindPoint.GRAPHICS
                    colorAttachments {
                        reference {
                            attachment = 0U
                            layout = ImageLayout.COLOR_ATTACHMENT_OPTIMAL
                        }
                    }
                }
            }
            dependencies {
                dependency {
                    srcSubpass = Vk.SUBPASS_EXTERNAL
                    dstSubpass = 0U

                    srcStageMask = PipelineStage.COLOR_ATTACHMENT_OUTPUT
                    srcAccessMask = null

                    dstStageMask = PipelineStage.COLOR_ATTACHMENT_OUTPUT
                    dstAccessMask = Access.COLOR_ATTACHMENT_READ or Access.COLOR_ATTACHMENT_WRITE
                }
            }
        }

        vertexBuffer = device.createBuffer {
            size = vertices.size.toULong() * 4u * (2u + 3u)
            usage = BufferUsage.VERTEX_BUFFER or BufferUsage.TRANSFER_DST
            sharingMode = SharingMode.EXCLUSIVE
        }
        vertexBufferMemory = device.allocateMemory {
            val memRequirements = vertexBuffer.memoryRequirements

            allocationSize = memRequirements.size
            memoryTypeIndex = findMemoryType(
                memRequirements.memoryTypeBits, MemoryProperty.DEVICE_LOCAL
            )
        }
        vertexBuffer.bindMemory(vertexBufferMemory, 0u)

        indexBuffer = device.createBuffer {
            size = indices.size.toULong() * 4u * (2u + 3u)
            usage = BufferUsage.INDEX_BUFFER or BufferUsage.TRANSFER_DST
            sharingMode = SharingMode.EXCLUSIVE
        }
        indexBufferMemory = device.allocateMemory {
            val memRequirements = indexBuffer.memoryRequirements

            allocationSize = memRequirements.size
            memoryTypeIndex = findMemoryType(
                memRequirements.memoryTypeBits, MemoryProperty.DEVICE_LOCAL
            )
        }
        indexBuffer.bindMemory(indexBufferMemory, 0u)

        stagingBuffer(vertices.size.toULong() * 4u * (2u + 3u)) { stagingBuffer, data ->
            for (vertex in vertices) {
                data.writeFloat(vertex.pos.x, ByteOrder.nativeOrder())
                data.writeFloat(vertex.pos.y, ByteOrder.nativeOrder())
                data.writeFloat(vertex.color.x, ByteOrder.nativeOrder())
                data.writeFloat(vertex.color.y, ByteOrder.nativeOrder())
                data.writeFloat(vertex.color.z, ByteOrder.nativeOrder())
            }

            val cmdBuf = commandPool.allocate(CommandBufferLevel.PRIMARY, 1U).single()
            with(cmdBuf) {
                begin { flags = CommandBufferUsage.ONE_TIME_SUBMIT }
                copyBuffer(stagingBuffer, vertexBuffer) {
                    copy {
                        srcOffset = 0U
                        dstOffset = 0U
                        size = stagingBuffer.size
                    }
                }
                end()
            }
            graphicsQueue.submit(null) { info(null, listOf(cmdBuf)) }
            graphicsQueue.waitIdle()
            cmdBuf.close()
        }

        stagingBuffer(indices.size.toULong() * UShort.SIZE_BYTES.toUInt()) { stagingBuffer, data ->
            when (ByteOrder.nativeOrder()) {
                ByteOrder.BIG_ENDIAN -> data.writeFully(indices.asShortArray())
                ByteOrder.LITTLE_ENDIAN -> data.writeFullyLittleEndian(indices.asShortArray())
            }

            val cmdBuf = commandPool.allocate(CommandBufferLevel.PRIMARY, 1U).single()
            with(cmdBuf) {
                begin { flags = CommandBufferUsage.ONE_TIME_SUBMIT }
                copyBuffer(stagingBuffer, indexBuffer) {
                    copy {
                        srcOffset = 0U
                        dstOffset = 0U
                        size = stagingBuffer.size
                    }
                }
                end()
            }
            graphicsQueue.submit(null) { info(null, listOf(cmdBuf)) }
            graphicsQueue.waitIdle()
            cmdBuf.close()
        }

        onRecreateSwapchain(swapchain)
    }

    private inline fun stagingBuffer(size: ULong, block: (Buffer, IoBuffer) -> Unit) {
        val stagingBuffer = device.createBuffer {
            this.size = size
            usage = BufferUsage.TRANSFER_SRC
            sharingMode = SharingMode.EXCLUSIVE
        }
        val memory = device.allocateMemory {
            val memRequirements = stagingBuffer.memoryRequirements

            allocationSize = memRequirements.size
            memoryTypeIndex = findMemoryType(
                memRequirements.memoryTypeBits,
                MemoryProperty.HOST_VISIBLE or MemoryProperty.HOST_COHERENT
            )
        }
        stagingBuffer.bindMemory(memory, 0UL)

        try {
            block(stagingBuffer, memory.map(0u, memory.size))
        } finally {
            memory.unmap()
            stagingBuffer.close()
            memory.close()
        }
    }

    private fun findMemoryType(typeFilter: UInt, properties: VkFlag<MemoryProperty>): UInt {
        val memoryProperties = device.physicalDevice.memoryProperties

        for ((index, type) in memoryProperties.memoryTypes.withIndex()) {
            if (typeFilter and (1u shl index) != 0u && properties in type.propertyFlags) {
                return index.toUInt()
            }
        }

        throw Exception("Failed to find suitable memory type!")
    }

    override fun onRecreateSwapchain(swapchain: SwapchainKHR) {
        val (swapchainWidth, swapchainHeight) = swapchain.imageExtent

        graphicsPipeline = device.createGraphicsPipelines {
            pipeline(pipelineLayout, renderPass) {
                subpass = 0U
                basePipelineIndex = -1

                stages {
                    stage(ShaderStage.VERTEX, vertShaderModule) {
                        name = "main"
                    }
                    stage(ShaderStage.FRAGMENT, fragShaderModule) {
                        name = "main"
                    }
                }
                vertexInputState {
                    vertexBindingDescriptions {
                        description {
                            binding = 0u
                            stride = 4u * (2u + 3u)
                            inputRate = VertexInputRate.VERTEX
                        }
                    }
                    vertexAttributeDescriptions {
                        description(0U, 0U, Format.R32G32_SFLOAT, 0u)
                        description(1U, 0U, Format.R32G32B32_SFLOAT, 8u)
                    }
                }
                inputAssemblyState {
                    topology = PrimitiveTopology.TRIANGLE_LIST
                    primitiveRestartEnable = false
                }
                viewportState {
                    viewports {
                        viewport {
                            x = 0f
                            y = 0f
                            width = swapchainWidth.toInt().toFloat()
                            height = swapchainHeight.toInt().toFloat()
                            minDepth = 0f
                            maxDepth = 1f
                        }
                    }
                    scissors {
                        rect2D {
                            offset(0, 0)
                            extent(swapchainWidth, swapchainHeight)
                        }
                    }
                }
                rasterizationState {
                    depthClampEnable = false
                    rasterizerDiscardEnable = false
                    polygonMode = PolygonMode.FILL
                    lineWidth = 1.0f
                    cullMode = CullMode.BACK
                    frontFace = FrontFace.CLOCKWISE

                    depthBiasEnable = false
                    depthBiasConstantFactor = 0.0f
                    depthBiasClamp = 0.0f
                    depthBiasSlopeFactor = 0.0f
                }
                multisampleState {
                    sampleShadingEnable = false
                    rasterizationSamples = SampleCount.`1`
                    minSampleShading = 1.0f
                    alphaToCoverageEnable = false
                    alphaToOneEnable = false
                }
                colorBlendState {
                    logicOpEnable = false
                    attachments {
                        state {
                            colorWriteMask = ColorComponent.R or ColorComponent.B or ColorComponent.G or ColorComponent.A
                            blendEnable = true
                            srcColorBlendFactor = BlendFactor.SRC_ALPHA
                            dstColorBlendFactor = BlendFactor.ONE_MINUS_SRC_ALPHA
                            colorBlendOp = BlendOp.ADD

                            srcAlphaBlendFactor = BlendFactor.ONE
                            dstAlphaBlendFactor = BlendFactor.ZERO
                            alphaBlendOp = BlendOp.ADD
                        }
                    }
                    blendConstants(0f, 0f, 0f, 0f)
                }
            }
        }.single()

        swapchainFramebuffers = swapchainImageViews.map { imageView ->
            device.createFramebuffer(renderPass, listOf(imageView)) {
                width = swapchainWidth
                height = swapchainHeight
                layers = 1u
            }
        }

        commandBuffers = commandPool.allocate(CommandBufferLevel.PRIMARY, swapchainFramebuffers.size.toUInt())
        commandBuffers.forEachIndexed { index, commandBuffer ->
            with(commandBuffer) {
                begin { flags = CommandBufferUsage.SIMULTANEOUS_USE }

                beginRenderPass(renderPass, swapchainFramebuffers[index], SubpassContents.INLINE) {
                    clearValues {
                        clearValue {
                            color(0f, 0f, 0f, 1f)
                        }
                    }
                    renderArea {
                        offset(0, 0)
                        extent(swapchainWidth, swapchainHeight)
                    }
                }

                bindPipeline(PipelineBindPoint.GRAPHICS, graphicsPipeline)

                bindVertexBuffers(0u, listOf(vertexBuffer to 0uL))
                bindIndexBuffer(indexBuffer, 0u, IndexType.UINT16)

                drawIndexed(indices.size.toUInt(), 1u, 0u, 0, 0u)

                endRenderPass()
                end()
            }
        }
    }

    override fun onDestroySwapchain() {
        swapchainFramebuffers.forEach { it.close() }
        graphicsPipeline.close()
        commandPool.free(commandBuffers)
    }

    override fun renderFrame(imageIndex: Int) {
        device.waitForFences(listOf(inFlightFences[currentFrame]), true)
        device.resetFences(listOf(inFlightFences[currentFrame]))

        graphicsQueue.submit(inFlightFences[currentFrame]) {
            info(
                listOf(imageAvailableSemaphores[currentFrame] to PipelineStage.COLOR_ATTACHMENT_OUTPUT),
                listOf(commandBuffers[imageIndex]),
                listOf(renderFinishedSemaphores[currentFrame])
            )
        }
    }

    override fun close() {
        device.waitIdle()

        onDestroySwapchain()

        indexBuffer.close()
        indexBuffer.memory?.close()
        vertexBuffer.close()
        vertexBuffer.memory?.close()
        renderPass.close()
        pipelineLayout.close()
        fragShaderModule.close()
        vertShaderModule.close()

        super.close()
    }
}
