# RE-winmali

A fork of Winlator Mali 
(https://github.com/GunaCharanTeja/WinlatorMali by GunaCharanTeja )

# What's new? 

 ## 🎨New UI
new horizontal and vertical UI 
<img width="1280" height="562" alt="image" src="https://github.com/user-attachments/assets/629fce2a-5c5c-49ad-9550-cc199d8d0034" />
<img width="1280" height="562" alt="image" src="https://github.com/user-attachments/assets/4b2626c6-ce37-4f86-b4ac-3177bc851350" />

<img width="93" height="213" alt="image" src="https://github.com/user-attachments/assets/65b81b58-f4bb-4ffb-af4a-24d6942f03a6" />
<img width="93" height="213" alt="image" src="https://github.com/user-attachments/assets/2c985142-cba7-4d1b-9b88-85e31548162f" />
<img width="93" height="213" alt="image" src="https://github.com/user-attachments/assets/7bb39318-7273-40af-8fa3-02f76a62869e" />

 
new shortcuts app UI with costomizable icons and backgrounds,vertical mode has two scrolling modes, vertical and horizontal mode have different art


## 👀New features 

### Direct PANVK driver import 
import your drivers easily 
<img width="1280" height="562" alt="image" src="https://github.com/user-attachments/assets/4a9d43d3-6c4c-441c-9c79-1e914e2f28bf" />

### 🫔Import wrapper option 
import any .tzst wrapper natively from the mange wrappers app
<img width="1280" height="562" alt="image" src="https://github.com/user-attachments/assets/ce8587e7-aee0-4b24-9866-c2a6a9fb0ba4" />

### 🚂Change touch controls engine
if you faced any problems with the touch or gamepad controls you can change the engine to the winlator mali 1.1 or the Bannerlator-3.1.8 engine as a fallback

### 👨‍💻New renderers 
  #### OpenGL+Gladio 
  from winlator mali 1.2 beta

  #### EGL 
  from latest winlator mali 1.2.1

  #### Vulkan 
  from Bannerlator-3.1.8-mali-pre1

 #### SurfaceFlinger 
  from Bannerlator-3.1.8-mali-pre1


## Why RE:winmali ?
You get all the winlator mali features with a new improved UI and new features 

Mali GPUs don't natively support the BCn texture formats (BC1–BC7) that PC games rely on, which caused black textures and missing models. Winlator Mali fixes this and more:

BCn texture emulation to ASTC / ETC2, with a persistent disk cache to remove repeat stutter
NFSR, SGSR, FXAA and other PostFX
ZeroFG frame generation, a Vulkan compute engine tuned for Mali
Real Mali telemetry HUD (GPU load, clocks, CPU, battery watts, frametimes)
Touch controls, gyro, USB gamepads and rumble, plus a live in-game controls editor
Community Configs with ratings, SteamGridDB artwork and a Save Manager

# Credits
Winlator Mali: GunaCharanTeja (Fcharan)
Winlator & Gladio: Bruno (brunodev85)
Bionic fork: Pipetto Cripto
BCn Vulkan layer: leegao
HUD & File Manager: stevenmxz
Controller fixes: vivsi1
Theme system & CI: Noysz
Community Configs idea: The412Banner
Reference implementations: longjunyu2
ZeroFG: hy300leosquizz-ctrl
Touch controls styling: inspired by Madeira
Vulakn and SurfaceFilnger and the bannerlator touch engine by The412Banner
# Licenses

This repository's code and the Vulkan, SurfaceFlinger, and touch-engine contributions from Bannerlator are licensed under the GNU General Public License, version 3.0 (GPL-3.0), consistent with Bannerlator's license:

> This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See [LICENSE](LICENSE). Third-party components retain their respective licenses:

- Wine/Proton: LGPL v2.1+
- Box64: MIT
- FEX-Emu: MIT
- Mesa: MIT/LGPL
- DXVK: zlib/libpng
- VKD3D: LGPL v2.1
- libadrenotools: GPL v3
- ZeroFG: Apache 2.0 / BSD 3-Clause
- Inter Font: SIL OFL 1.1
- AOSP: Apache 2.0
  
