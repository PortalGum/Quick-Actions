# ⚡ Quick Actions (QA) | (Fabric)

![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)  ![Client](https://img.shields.io/badge/Environment-Client-informational?logo=java)
 ![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green)

A small **client-side** mod that adds **right-click menus** to Minecraft's lists: worlds, servers, resource packs and screenshots.

Open a folder, copy a path, back up a world, reorder servers, save a pack preset - all in one click, right where you need it.

> [!NOTE]
> **Client-side only.** Nothing to install on the server, and it works on any server you join.


[![Modrinth](https://img.shields.io/badge/Modrinth-View%20Project-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/project/T1rCeI8G)
[![CurseForge](https://img.shields.io/badge/CurseForge-View%20Project-f16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/quick-actions-qa)

---

## 🤔 What the mod does

Vanilla Minecraft makes you leave the game for simple things: find a folder, rename a file, copy a path, make a backup.

Quick Actions puts these actions **into the game itself**:

* **right-click** an entry in a list → a menu with the actions that make sense for it
* **right-click empty space** in a list → tools for the whole list (backups, import, presets...)
* a **Screenshots** button and a **settings** button in the top-left corner of the title screen and the pause menu

👉 Small, obvious actions only. No bloat.

---

## 🌍 Worlds

Right-click a world:

<img width="1712" height="980" alt="image" src="https://github.com/user-attachments/assets/30193001-384a-457c-bcc2-23087ed0a39c" />


| Action | What it does |
| ------ | ------------ |
| Open World Folder | Opens the folder in your file explorer |
| Copy Folder Path | Copies the full path |
| Create Backup | Saves a zip backup of the world |
| Restore Backup... | Roll the world back, or restore it as a **new** world |
| Duplicate World | Creates a copy next to the original |
| Edit | Rename, Change Icon, Reset Icon |
| Delete World | Deletes the world after confirmation |

Right-click empty space → **All Backups**: every backup of every world, grouped per world. Rename backups, roll a world back (with confirmation) or create a separate world from any backup.
<img width="1267" height="1439" alt="image" src="https://github.com/user-attachments/assets/bd570516-408a-4118-81e5-06e9e36906e3" />

---

## 🖥️ Servers

Right-click a server:


| Action | What it does |
| ------ | ------------ |
| Refresh | Pings this server again |
| Copy IP | Copies the address |
| Edit | Rename, change Address |
| Hide | Hide the **MOTD**, **Online** counter or **Icon** of this server |
| Delete | Removes the server after confirmation |

Right-click empty space:

* **Refresh All**
* **Hide (All Servers)** - global defaults; a server's own setting overrides them
* **Export / Import Server List...** - saved as JSON, import by *adding* or *replacing*
<img width="1009" height="1439" alt="image" src="https://github.com/user-attachments/assets/fa1875d6-1f23-4164-8039-601586d8124b" />

* **Clear List** - with a confirmation, and an offer to export the list first

✋ **Hold and drag** a server to reorder the list.

<img width="1562" height="1064" alt="image" src="https://github.com/user-attachments/assets/e0ebce8d-642e-408b-8044-e0af7844d6cc" />
---

## 🎨 Resource Packs

Right-click a pack:

<img width="1625" height="964" alt="image" src="https://github.com/user-attachments/assets/8019a2eb-084b-4ff4-8d2e-c7e1352c1da9" />

| Action | What it does |
| ------ | ------------ |
| Show in Folder | Opens the folder with the pack selected |
| Copy Path | Copies the full path |
| Move to Top / Bottom | Changes the pack's priority in one click |
| Edit | Rename, Description, Change Icon, Reset Icon, Fix Compatibility |
| Duplicate Pack | Creates a copy next to the original |
| Delete | Deletes the pack permanently |

Right-click empty space: **Disable All Packs**, **Save Preset...**, **Presets...**
* **Description editor** - live preview, supports `&#rrggbb` HEX colors and gradients 
<img width="1530" height="925" alt="image" src="https://github.com/user-attachments/assets/9ef2741d-d640-43e8-9f1b-725eabceae3c" />

* **Presets** - save the enabled packs *and their order*, switch setups in one click
<img width="1496" height="1439" alt="image" src="https://github.com/user-attachments/assets/7877079d-0238-4339-a2f4-04a0d5240d19" />

* **Edits work inside `.zip` packs too** - the archive is rewritten for you

✋ **Hold and drag** enabled packs to reorder them.

> [!TIP]
> **Fix Compatibility** only changes the supported version range in `pack.mcmeta`, so the game stops marking the pack as incompatible. It does **not** update textures: if a pack uses outdated file names, some textures may still not work.

---

## 📷 Screenshots

Press the **Screenshots** button in the top-left corner (title screen or pause menu) to open a gallery of your screenshots.

* grouped by date: *Today*, *Yesterday*, ...
* large preview with resolution, format and file size
* full-screen viewer: `←` `→` browse, `Esc` close, `Del` delete
* Hold `Ctrl` + scroll the mouse wheel to zoom the image.
* **Select** mode: click or hold and drag to select many, then delete them together

Right-click a screenshot:

| Action | What it does |
| ------ | ------------ |
| Show in Folder | Opens the folder with the file selected |
| Copy to Clipboard | Copies the image, ready to paste into chats and editors |
| Copy Path | Copies the full path |
| Save As... | Saves a copy wherever you choose |
| Edit | Rename |
| Delete | Deletes permanently after confirmation |

<img width="1883" height="1438" alt="image" src="https://github.com/user-attachments/assets/5caa8f8d-7009-4f5f-8937-d7a46dc4ec20" />

---

## ⚙️ Configuration

Press the **gear** button in the top-left corner (title screen or pause menu)

* Turn Quick Actions on or off
* Toggle each menu separately: **worlds**, **servers**, **resource packs**, **screenshots**
* Toggle drag-and-drop for servers and packs
* Show or hide the **Screenshots** button
* Change the **theme** - ready-made themes, your own colors and your own presets
* Quick access to the important folders (backups, presets, screenshots, server lists)

Changes are saved automatically. Config file: `config/quickactions.json`

<img width="1141" height="1439" alt="image" src="https://github.com/user-attachments/assets/6e49bdef-a8fb-4507-bff2-0e5db486f84a" />

---

## 🎨 Look and feel

Menus use a dark, vanilla-friendly style. Pick one of the built-in themes or choose your own colors.

`classic` · `midnight` · `ocean` · `emerald` · `lime` · `amber` · `sunset` · `crimson` · `rose` · `violet` · `graphite` · `mono`

<img width="855" height="1439" alt="image" src="https://github.com/user-attachments/assets/bb61aa9c-72ee-467a-b56f-79216b9a0ef3" />

---

## 🌐 Languages

English · Русский · Deutsch · Español · Français · Polski · Português (Brasil) · 中文 (简体)

---

## ❗ Requirements

* **Minecraft 26.3**
* **Fabric Loader** `0.19.3` or newer
* **Fabric API**
* **Java 25**

---

## 🐞 Issues and feedback

Found a bug or have an idea? Open an issue on [GitHub](https://github.com/PortalGum/Quick-Actions/issues).

Also available on [Modrinth](https://modrinth.com/project/T1rCeI8G).

---

## 📄 License

[MIT](LICENSE)
