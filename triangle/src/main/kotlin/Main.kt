import com.kgl.vulkan.Vk
import com.kgl.vulkan.enums.*
import com.kgl.vulkan.handles.*
import com.kgl.vulkan.utils.or
import kotlinx.io.core.use
import sample.utils.BaseApplication
import sample.utils.readAllBytes

fun main(args: Array<String>) {
    TriangleApplication().use {
        it.mainLoop()
    }
}

const val RESOURCE_DIR = "."

class TriangleApplication : BaseApplication() {
    private val vertShaderModule: ShaderModule
    private val fragShaderModule: ShaderModule
    private val pipelineLayout: PipelineLayout
    private val renderPass: RenderPass

    private lateinit var swapchainFramebuffers: List<Framebuffer>
    private lateinit var graphicsPipeline: Pipeline
    private lateinit var commandBuffers: List<CommandBuffer>

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

        onRecreateSwapchain(swapchain)
    }

    override fun onRecreateSwapchain(swapchain: SwapchainKHR) {
        val swapchainWidth = swapchain.imageExtent.width
        val swapchainHeight = swapchain.imageExtent.height

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
                draw(3U, 1U, 0U, 0U)

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

        renderPass.close()
        pipelineLayout.close()
        fragShaderModule.close()
        vertShaderModule.close()

        super.close()
    }
}
