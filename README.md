# Android TV Launcher

[English](#android-tv-launcher) · [Русский](#русский)

A lightweight Android TV launcher written in Java using only the Android SDK. Supports Android 6.0 and later. The interface follows the device language: Russian is supported, with English used for other languages.

- Launch apps and add them to the home screen; rows grow automatically.
- Hold Select or press Menu to move, hide, remove, or manage an app. While moving, use the arrows, Select to save, or Back to cancel.
- Open the remaining apps with the All apps button or by pressing Down on the last home row. Back collapses the list.
- Use launcher settings to change app label visibility, restore hidden apps, reset settings, or manage installed apps, including system apps.

## Build

Requires JDK 17, Android SDK Platform 35 and Build Tools 34.0.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK path and run:

```sh
./build.sh
```

The output is `build/launcher.apk`. On the first build, the script generates a local `debug.keystore`. Keep it to install future builds over earlier ones. The debug key is excluded from Git and is not intended for release distribution; sign release builds with your own securely stored release key.

For a release build, keep your keystore outside the repository and run:

```sh
./build.sh release /path/to/release.jks your-key-alias
```

Enter the keystore password when prompted. The signed, verified output is `build/launcher-release.apk`. Use the same signing key for future updates. Do not commit your keystore or password.

Install the APK on the TV and choose TV Launcher as the home app in the system dialog or Android TV settings.

## Русский

Лёгкий лаунчер для Android TV на Java и стандартном Android SDK, без сторонних библиотек. Поддерживается Android 6.0 и новее. Язык интерфейса выбирается по настройкам устройства: доступны русский и английский.

### Возможности

- Добавляйте приложения на главный экран: ряды создаются автоматически.
- Удерживайте кнопку выбора или нажмите Menu, чтобы переместить, скрыть, убрать или удалить приложение. При перемещении используйте стрелки, кнопку выбора для сохранения и «Назад» для отмены.
- Открывайте остальные приложения кнопкой «Все приложения» или стрелкой вниз на последнем ряду. Кнопка «Назад» сворачивает список.
- В настройках можно изменить отображение названий, вернуть скрытые приложения, сбросить настройки и управлять установленными приложениями, включая системные.

### Сборка

Понадобятся JDK 17, Android SDK Platform 35 и Build Tools 34.0.0. Укажите путь к SDK в `ANDROID_HOME` или `ANDROID_SDK_ROOT` и выполните:

```sh
./build.sh
```

Результат — `build/launcher.apk`. При первой сборке скрипт создаст локальный отладочный ключ `debug.keystore`. Сохраните его, чтобы устанавливать последующие отладочные сборки поверх предыдущих. Этот ключ не подходит для публичного релиза.

### Релизная сборка

Храните ключ вне репозитория и выполните:

```sh
./build.sh release /путь/к/ключу.jks alias
```

Скрипт запросит пароль, подпишет APK и проверит подпись. Результат — `build/launcher-release.apk`. Для последующих обновлений используйте тот же ключ. Не добавляйте ключ и пароль в репозиторий.

### Установка

Установите APK на телевизор и выберите лаунчер как приложение главного экрана в системном диалоге или настройках Android TV.
