# Smooth caret Intellij plugin

# Updates

Thanks to TheTeaParty for open sourcing，Thanks to amuazm for updating the project to be compatible with the latest IntelliJ 2025.2 version.



VSCode-like smooth caret plugin for Intellij IDEA

![Smooth Caret Demo](https://github.com/TheTeaParty/intellij-smooth-caret/blob/main/images/example.gif?raw=true)

## Installation
[Install from JetBrains Marketplace](https://plugins.jetbrains.com/plugin/26838-smoothcaret)

## Configuration
You can configure the plugin in `Settings > Editor > Smooth Caret`

![Smooth Caret Settings](https://github.com/TheTeaParty/intellij-smooth-caret/blob/main/images/settings.png?raw=true)

### Basic Settings
- **Enable blinking**: Toggle caret blinking animation. When disabled, the caret remains constantly visible.

### Animation Settings
- **Animation smoothness** (5-30%): Controls how smooth the caret movement is. Lower values create smoother animations, while higher values make the movement more direct.

- **Catch-up speed** (30-80%): Determines how quickly the caret catches up during normal typing. Higher values make the caret more responsive but less smooth.

- **Max catch-up speed** (50-100%): Sets the maximum speed for catching up during fast typing. This ensures the caret doesn't fall too far behind during rapid input.

- **Adaptive speed**: When enabled, automatically adjusts animation speed based on typing speed.

### Default Values
- Animation smoothness: 15%
- Catch-up speed: 50%
- Max catch-up speed: 80%
- Adaptive speed: Enabled
- Blinking: Enabled
