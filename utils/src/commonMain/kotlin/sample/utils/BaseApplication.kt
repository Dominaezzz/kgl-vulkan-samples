package sample.utils

import com.kgl.glfw.*
import com.kgl.vulkan.Vk
import com.kgl.vulkan.enums.*
import com.kgl.vulkan.handles.*
import com.kgl.vulkan.structs.Extent2D
import com.kgl.vulkan.structs.SurfaceFormatKHR
import com.kgl.vulkan.utils.*
import kotlinx.io.core.Closeable

val validationLayers = arrayOf(
    "VK_LAYER_LUNARG_standard_validation"
)
val deviceExtensions = arrayOf(
    Vk.KHR_SWAPCHAIN_EXTENSION_NAME
)

const val WIDTH = 800
const val HEIGHT = 600
const val MAX_FRAMES_IN_FLIGHT = 2

abstract class BaseApplication : Closeable {
    private val window: Window
    private val instance: Instance
    private val callback: DebugUtilsMessengerEXT
    private val surface: SurfaceKHR
    private val physicalDevice: PhysicalDevice
    protected val device: Device
    protected val presentQueue: Queue
    protected val graphicsQueue: Queue

    protected val imageAvailableSemaphores: Array<Semaphore>
    protected val renderFinishedSemaphores: Array<Semaphore>
    protected val inFlightFences: Array<Fence>

    protected val commandPool: CommandPool

    protected val surfaceFormat: SurfaceFormatKHR
    protected lateinit var swapchain: SwapchainKHR
    protected lateinit var swapchainImageViews: List<ImageView>

    init {
        window = Window(WIDTH, HEIGHT, "Sample!", null, null) {
            clientApi = ClientApi.None
            // resizable = false
        }

        val (width, height) = window.size
        val mode = Glfw.primaryMonitor!!.videoMode
        window.position = ((mode.width - width) / 2) to ((mode.height - height) / 2)

        window.setFrameBufferCallback { _, _, _ ->
            framebufferResized = true
        }

        check(isVulkanSupported) { "Vulkan isn't supported!!!" }

        val extensions = requiredInstanceExtensions!!.asList() + Vk.EXT_DEBUG_REPORT_EXTENSION_NAME + Vk.EXT_DEBUG_UTILS_EXTENSION_NAME

        val layers = Instance.layerProperties.map { it.layerName }.toSet()
        check(layers.containsAll(validationLayers.asList()))

        instance = Instance.create(validationLayers.asList(), extensions) {
            applicationInfo {
                applicationName = "Hello Triangle"
                applicationVersion = VkVersion(1U, 0U, 0U)
                engineName = "No Engine"
                engineVersion = VkVersion(1U, 0U, 0U)
                apiVersion = VkVersion(1U, 0U, 0U)
            }
        }

        callback = instance.createDebugUtilsMessengerEXT(
            DebugUtilsMessageTypeEXT.GENERAL or DebugUtilsMessageTypeEXT.PERFORMANCE or DebugUtilsMessageTypeEXT.VALIDATION,
            DebugUtilsMessageSeverityEXT.VERBOSE or DebugUtilsMessageSeverityEXT.WARNING or DebugUtilsMessageSeverityEXT.INFO or DebugUtilsMessageSeverityEXT.ERROR
        ) { _, _, data ->
            // println("Validation layer: ${data.pMessage}")
        }
    }
    init {
        surface = instance.createWindowSurface(window)

        var graphics = -1
        var present = -1

        physicalDevice = instance.physicalDevices.first { device ->
            for ((i, prop) in device.queueFamilyProperties.withIndex()) {
                if (prop.queueCount <= 0U) continue

                if (QueueFlag.GRAPHICS in prop.queueFlags) {
                    graphics = i
                }

                if (device.getSurfaceSupportKHR(i.toUInt(), surface)) {
                    present = i
                }

                if (graphics != -1 && present != -1) break
            }

            val extensionProps = device.getDeviceExtensionProperties()

            val extensionSupported = deviceExtensions.all { de -> extensionProps.any { it.extensionName == de } }

            graphics != -1 && present != -1 &&
                    extensionSupported &&
                    device.getSurfaceFormatsKHR(surface).isNotEmpty() &&
                    device.getSurfacePresentModesKHR(surface).isNotEmpty() &&
                    device.features.samplerAnisotropy
        }
        surfaceFormat = physicalDevice.getSurfaceFormatsKHR(surface).let { formats ->
            if (formats.size == 1 && formats[0].format == Format.UNDEFINED) {
                SurfaceFormatKHR(Format.R8G8B8A8_UNORM, ColorSpaceKHR.SRGB_NONLINEAR)
            } else {
                formats.firstOrNull {
                    it.format == Format.R8G8B8A8_UNORM && it.colorSpace == ColorSpaceKHR.SRGB_NONLINEAR
                } ?: formats[0]
            }
        }

        device = physicalDevice.createDevice(validationLayers.asList(), deviceExtensions.asList()) {
            queues {
                for (family in setOf(graphics, present)) {
                    queue(family.toUInt(), 1.0F)
                }
            }

            enabledFeatures {
                samplerAnisotropy = true
            }
        }

        presentQueue = device.getQueue(present.toUInt(), 0U)
        graphicsQueue = device.getQueue(graphics.toUInt(), 0U)

        imageAvailableSemaphores = Array(MAX_FRAMES_IN_FLIGHT) { device.createSemaphore() }
        renderFinishedSemaphores = Array(MAX_FRAMES_IN_FLIGHT) { device.createSemaphore() }
        inFlightFences = Array(MAX_FRAMES_IN_FLIGHT) { device.createFence { flags = FenceCreate.SIGNALED } }
        commandPool = device.createCommandPool(graphicsQueue.queueFamilyIndex)

        createSwapchain()
    }

    private fun createSwapchain() {
        val sharingMode: SharingMode
        val indices: UIntArray?
        if (graphicsQueue.queueFamilyIndex != presentQueue.queueFamilyIndex) {
            sharingMode = SharingMode.CONCURRENT
            indices = uintArrayOf(graphicsQueue.queueFamilyIndex, presentQueue.queueFamilyIndex)
        } else {
            sharingMode = SharingMode.EXCLUSIVE
            indices = null
        }

        val surfaceCapabilities = physicalDevice.getSurfaceCapabilitiesKHR(surface)

        swapchain = device.createSwapchainKHR(surface, indices) {
            imageFormat = surfaceFormat.format
            imageColorSpace = surfaceFormat.colorSpace

            minImageCount = if (surfaceCapabilities.maxImageCount != 0U) {
                (surfaceCapabilities.minImageCount + 1U).coerceAtMost(surfaceCapabilities.maxImageCount)
            } else {
                surfaceCapabilities.minImageCount + 1U
            }

            if (surfaceCapabilities.currentExtent.width == UInt.MAX_VALUE) {
                val (width, height) = window.frameBufferSize
                imageExtent(
                    width.toUInt().coerceIn(surfaceCapabilities.minImageExtent.width, surfaceCapabilities.maxImageExtent.width),
                    height.toUInt().coerceIn(surfaceCapabilities.minImageExtent.height, surfaceCapabilities.maxImageExtent.height)
                )
            } else {
                imageExtent(surfaceCapabilities.currentExtent.width, surfaceCapabilities.currentExtent.height)
            }

            imageArrayLayers = 1u
            imageUsage = ImageUsage.COLOR_ATTACHMENT
            imageSharingMode = sharingMode
            preTransform = surfaceCapabilities.currentTransform
            compositeAlpha = CompositeAlphaKHR.OPAQUE

            presentMode = physicalDevice.getSurfacePresentModesKHR(surface).run {
                firstOrNull { it == PresentModeKHR.MAILBOX } ?:
                firstOrNull { it == PresentModeKHR.IMMEDIATE } ?:
                PresentModeKHR.FIFO
            }
            clipped = true
        }

        swapchainImageViews = swapchain.images.map { image ->
            image.createView(ImageViewType.`2D`, image.format) {
                components {
                    r = ComponentSwizzle.IDENTITY
                    g = ComponentSwizzle.IDENTITY
                    b = ComponentSwizzle.IDENTITY
                    a = ComponentSwizzle.IDENTITY
                }
                subresourceRange {
                    aspectMask = ImageAspect.COLOR
                    baseMipLevel = 0U
                    levelCount = 1U
                    baseArrayLayer = 0U
                    layerCount = 1U
                }
            }
        }
    }
    private fun cleanupSwapchain() {
        swapchainImageViews.forEach { it.close() }
        swapchain.close()
    }
    private fun recreateSwapchain() {
        framebufferResized = false
        while (window.frameBufferSize.let { it.first == 0 || it.second == 0 }) {
            waitEvents()
        }

        device.waitIdle()

        onDestroySwapchain()
        cleanupSwapchain()
        createSwapchain()
        onRecreateSwapchain(swapchain)
    }

    protected abstract fun onRecreateSwapchain(swapchain: SwapchainKHR)
    protected abstract fun onDestroySwapchain()
    protected abstract fun renderFrame(imageIndex: Int)

    fun mainLoop() {
        while (!window.shouldClose) {
            pollEvents()

            drawFrame()
        }
    }

    protected var currentFrame = 0
    private var framebufferResized = false

    private fun drawFrame() {
        val acquireResult: Acquire
        try {
            acquireResult = swapchain.acquireNextImage(ULong.MAX_VALUE, imageAvailableSemaphores[currentFrame])
        } catch (e: OutOfDateKhrError) {
            device.waitForFences(listOf(inFlightFences[currentFrame]), true)
            recreateSwapchain()
            return
        }

        val imageIndex = (acquireResult as Acquire.Success).imageIndex

        renderFrame(imageIndex.toInt())

        try {
            val success = presentQueue.presentKHR(
                listOf(swapchain to imageIndex),
                listOf(renderFinishedSemaphores[currentFrame])
            )
            if (!success || framebufferResized) {
                framebufferResized = false
                recreateSwapchain()
            }
        } catch (e: OutOfDateKhrError) {
            framebufferResized = false
            recreateSwapchain()
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT
    }

    override fun close() {
        device.waitIdle()

        cleanupSwapchain()

        commandPool.close()
        inFlightFences.forEach { it.close() }
        renderFinishedSemaphores.forEach { it.close() }
        imageAvailableSemaphores.forEach { it.close() }

        device.close()
        surface.close()
        callback.close()
        instance.close()
        window.close()
    }
}
