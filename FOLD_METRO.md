# MagicFold / Magic V2 — v0.6

Launcher Android amb estètica Windows 10 Mobile i mosaics translúcids amb vores de llum.
El temps de **Cabrils** transforma el fons: paisatge en capes, núvols que es desplacen,
pluja o neu i cel de dia o de nit segons les dades d’Open-Meteo. Sense dades,
es mostra un paisatge neutre sense inventar meteorologia. El relleu és una
il·lustració estilitzada, no una representació geogràfica exacta de Cabrils.

En desplegar el mòbil el paisatge s’obre amb profunditat i els mosaics entren en
cascada. Arrossegar el dit produeix un paral·laxi suau sense impedir els gestos
normals de les apps. El moviment es limita a 30 fps, s’atura en perdre el focus,
en sortir del launcher, amb estalvi de bateria o amb animacions del sistema
desactivades. **Configuració → Efectes visuals** permet desactivar-lo.
**Configuració → Prova els paisatges vius** mostra simulacions de sol, núvols,
pluja, neu i nit, identificades explícitament, sense canviar el temps real.

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

Descarrega **MagicFold-Android** de la darrera execució correcta de
[MagicFold APK](https://github.com/skirep/eink-dashboard/actions/workflows/foldable.yml).
Instal·la `app-foldable-debug.apk` i estableix **MagicFold** com a inici.
Identificador `com.eink.launcher.foldable`, independent de la variant e-ink.
Pulsació llarga en una app per fixar-la o treure-la. **Trucades**, **SMS** i **Calendaris** queden fixos a la barra inferior, tant a inici
com al llistat d’apps. Trucades obre el marcador sense trucar automàticament;
SMS obre l’aplicació de missatgeria predeterminada.
Llisca **cap a la dreta** per veure totes les apps amb cerca; cap a l’esquerra
per tornar. El text indicatiu també és activable amb TalkBack o teclat.
Els gestos verticals continuen desplaçant el contingut.
A **Configuració → Launcher per defecte** pots seleccionar MagicFold com a inici;
també apareix a Configuració d’Android → Aplicacions predeterminades → Aplicació d’inici.

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

## Imatge de fons personal

**Configuració → Imatge de fons → Tria una imatge del mòbil** obre el selector
d’Android. La foto s’adapta sense deformar-se: omple la pantalla amb retall central
i manté un moviment suau de paral·laxi. Sobre la foto apareixen els núvols, la pluja,
la neu i la llum de dia/nit de Cabrils; el paisatge dibuixat deixa de tapar-la.
Pots desactivar aquests efectes sobre la foto o tornar al paisatge original.
Les mostres de paisatges vius també previsualitzen els efectes sobre la foto escollida.

S’importa una còpia privada de la imatge (màxim d’entrada 50 MB, costat de treball
màxim 2048 píxels) amb orientació corregida. Es conserva després de reiniciar,
sense dependre de la ubicació original. No es modifica ni es puja la foto original.
Les imatges no vàlides mantenen el fons anterior. No es demanen permisos generals
d’accés a la galeria. Una capa fosca manté els textos llegibles fins i tot amb fotos clares.
