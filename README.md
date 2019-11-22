# KGL Vulkan Samples

[![Build Status](https://travis-ci.com/Dominaezzz/kgl-vulkan-samples.svg?branch=master)](https://travis-ci.com/Dominaezzz/kgl-vulkan-samples)
[![Build status](https://ci.appveyor.com/api/projects/status/github/Dominaezzz/kgl-vulkan-samples?branch=master&svg=true)](https://ci.appveyor.com/project/Dominaezzz/kgl-vulkan-samples)
[![](https://github.com/Dominaezzz/kgl-vulkan-samples/workflows/Build/badge.svg)](https://github.com/Dominaezzz/kgl-vulkan-samples/actions)

A collection of open source samples for [Vulkan®](https://www.khronos.org/vulkan/) written using the help of [kgl](https://github.com/Dominaezzz/kgl).
Inspired by examples from [Sascha Willems](https://github.com/SaschaWillems/Vulkan) and [vulkan-tutorial](https://vulkan-tutorial.com/).

## Building
### Windows
- [Vulkan SDK](https://vulkan.lunarg.com/sdk/home).
### Linux
- `sudo apt install glfw3`
- [Vulkan SDK](https://vulkan.lunarg.com/sdk/home).
### macOS
- `brew install glfw --HEAD`
- `brew cask install apenngrace/vulkan/vulkan-sdk` or [Vulkan SDK](https://vulkan.lunarg.com/sdk/home).
- `export VULKAN_SDK=vulkansdk/macOS`. Replace the "vulkansdk" above with the actual path to your SDK. Make sure you include the /macOS part.
- `export DYLD_LIBRARY_PATH="$VULKAN_SDK/lib:$DYLD_LIBRARY_PATH"`
- `export VK_ICD_FILENAMES="$VULKAN_SDK/etc/vulkan/icd.d/MoltenVK_icd.json"`
- `export VK_LAYER_PATH="$VULKAN_SDK/etc/vulkan/explicit_layer.d"`
- `export PATH="$VULKAN_SDK/bin:$PATH"`

## Examples

### [1 - Triangle](triangle/src/commonMain/kotlin/Main.kt)
Simple render of a colourful triangle. This is as simple as vulkan gets.

### [2 - Buffers](buffers/src/commonMain/kotlin/Main.kt)
Simple render of a rectangle using a vertex buffer and an index buffer, each updated with a staging buffer.
