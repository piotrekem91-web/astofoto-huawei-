# Astro Pro P40

Natywna aplikacja Android dla Huawei P40 Pro (Android 10 / EMUI), bez Google Mobile Services.

## Wersja 0.1
- Podgląd aparatu przez Camera2
- Ręczne ISO i czas ekspozycji, o ile moduł aparatu udostępnia sterowanie manualne
- Zapis JPEG do Pictures/AstroProP40
- Odczyt zakresów ekspozycji z możliwości sprzętowych telefonu

## Budowanie APK
JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3. W Android Studio wybierz Build > Build APK(s).
GitHub Actions buduje debug APK po pushu i publikuje ją jako artefakt workflow.

## Kolejne etapy
RAW/DNG, serie ekspozycji, focus manual, stackowanie gwiazd, kalibracja dark/flat/bias i edytor obrazu wymagają dalszej implementacji i testów na fizycznym P40 Pro. Dostępność funkcji zależy od Camera2 i firmware Huawei.
