# MedApp — Android-клиент

Мобильный клиент [MedApp](https://github.com/Kert0n/MedApp): учёт домашней аптечки, общие аптечки,
синхронизация офлайн-изменений с [сервером](https://github.com/Kert0n/MedApp-Server).

Сейчас это заготовка из шаблона Android Studio: экран-пустышка на Compose и тема. Работа над
клиентом впереди.

## Сборка

```bash
./gradlew assembleDebug
```

Понадобится Android SDK; путь к нему — в `local.properties`, в git этот файл не попадает.

## Контракт сервера

Полный контракт — [open-api.yaml](https://github.com/Kert0n/MedApp-Server/blob/main/open-api.yaml)
в репозитории сервера; его можно открыть, не поднимая приложение.
