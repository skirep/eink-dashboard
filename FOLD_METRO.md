# Fold Metro / Magic V2 — v0.3

Launcher Android amb estètica Windows 10 Mobile: mosaics amb degradats, fons OLED
amb llum ambiental i il·lustracions meteorològiques natives. En desplegar el mòbil,
una transició breu reorganitza el contingut en dos panells. Els efectes es poden
desactivar i es respecten l’estalvi de bateria i les animacions desactivades del sistema.

## Dues ciutats

**Cabrils sempre queda fix**. Toca **Tria una ciutat** o **Configuració → Segona ciutat**
per cercar una segona ubicació i escollir-la d’una llista de coincidències.
Cada ciutat té dades, hora d’actualització i memòria cau independents.
No es fan servir dades inventades si falla la xarxa. Mantén premuda la segona ciutat
per canviar-la; la configuració també permet eliminar-la. Toca Cabrils per a la previsió.
Si a v0.2 s’havia triat una altra ciutat, es conserva ara com a segona ubicació.

## Google Calendar i Outlook

L’agenda combina els calendaris accessibles al perfil d’Android del launcher.
**Calendaris** permet seleccionar-los individualment i mostra nom, compte i origen.
Els actes indiquen Google, Outlook / Microsoft o el seu calendari d’origen.
La lectura és local: no s’envien esdeveniments als serveis del temps.

- **Google:** compte afegit a Android amb sincronització de Calendari activa.
- **Outlook:** a Outlook per Android → Configuració → compte → **Sincronitza els calendaris**.
  Torna al launcher i escull els calendaris exportats al calendari d’Android.
- Els comptes de feina poden prohibir aquesta exportació o situar els calendaris
  en un perfil de treball separat. El launcher no accedeix a dades d’un altre perfil
  ni evita les restriccions de l’empresa.
- No és una connexió OAuth directa a Google o Microsoft: depèn de la sincronització
  del dispositiu. No cal registrar credencials d’aplicació ni compartir contrasenyes.

Font de Microsoft: https://support.microsoft.com/en-us/outlook/how-do-i-sync-my-outlook-calendar-to-the-default-calendar-app-s

## Instal·lació i ús

Descarrega **Fold-Metro-Android** de la darrera execució correcta de
[Fold Metro APK](https://github.com/skirep/eink-dashboard/actions/workflows/foldable.yml).
Instal·la `app-foldable-debug.apk` i estableix **Fold Metro** com a inici.
Identificador `com.eink.launcher.foldable`, independent de la variant e-ink.
Pulsació llarga en una app per fixar-la o treure-la. El botó Aplicacions sempre és visible.

## Adaptació al plegable

Jetpack WindowManager observa la frontissa; es reserva l’espai quan separa la finestra.
Si MagicOS no exposa aquesta informació, s’adapta a partir de 600 dp d’amplada.
La cerca, vista, mosaics i desplaçament es conserven durant canvis de mida i recreació.
No es presenta una finestra ampla com a prova de l’estat físic de la frontissa.
Cal provar el comportament físic en un Honor Magic V2.

## Compilació i verificació

JDK 17 i Android SDK 34:

```sh
./gradlew testFoldableDebugUnitTest assembleFoldableDebug lintFoldableDebug assembleEinkDebug
./gradlew connectedFoldableDebugAndroidTest
```

El workflow publica APK, informes i captures en format compacte i ampli.
Les proves Android verifiquen memòria cau independent i descarten respostes de
ciutats antigues, combinen i filtren calendaris amb els adaptadors Google/Microsoft,
i comproven cerca i recreació en canviar de mida. Les captures `agenda-test-fixtures`
contenen actes de prova explícits, no dades personals reals ni una connexió a comptes remots.

Open-Meteo rep només coordenades de les ciutats seleccionades, amb atribució a l’app.
El servei gratuït té condicions d’ús no comercial: https://open-meteo.com/en/terms
