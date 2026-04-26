# CONFIGURACIÓ DE L'API DEL TEMPS

Per obtenir dades reals del temps, necessites configurar una API key gratuïta d'OpenWeatherMap:

## Passos per obtenir l'API key:

1. Registra't gratuïtament a: https://openweathermap.org/api
2. Vés a **API keys** al teu perfil
3. Copia la teva API key
4. Obre el fitxer: `app/src/main/java/com/eink/launcher/repository/WeatherRepository.kt`
5. Reemplaça `YOUR_API_KEY_HERE` amb la teva API key:

```kotlin
private val API_KEY = "la_teva_api_key_aqui"
```

## Notes importants:

- El pla gratuït permet 1.000 crides per dia (més que suficient per un launcher)
- L'API key pot trigar uns minuts a activar-se després del registre
- Les dades es mostren en català (`lang=ca`) i en graus Celsius
- La ubicació per defecte és Barcelona, però es pot canviar a les preferències

## Dades emmagatzemades localment:

- **Notes**: Es guarden automàticament en SharedPreferences
- **Llibre actual**: També en SharedPreferences
- **Temps**: Es recarrega cada vegada que s'obre la pantalla d'inici

## APIs gratuïtes alternatives:

Si prefereixes altres serveis, també pots utilitzar:
- **WeatherAPI** (https://www.weatherapi.com/) - 1M crides/mes gratuïtes
- **Open-Meteo** (https://open-meteo.com/) - Completament gratuït, sense API key

Caldria adaptar els models i serveis a l'API que triïs.
