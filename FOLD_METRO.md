# MagicFold / Magic V2 — v0.14

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
**Configuració → Calendaris Google i Outlook** permet seleccionar-los individualment i mostra nom, compte i origen.
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
Pulsació llarga en una app per fixar-la o treure-la. **Trucades**, **WhatsApp** i **Agenda** queden fixos a la barra inferior, tant a inici
com al llistat d’apps. Trucades obre el marcador sense trucar automàticament;
WhatsApp obre l’app instal·lada al mateix perfil; si no hi és, es prova WhatsApp Business.
Les aplicacions són una **segona pàgina situada a la dreta** de l’inici.
Llisca **cap a l’esquerra** per anar-hi; cap a la dreta per tornar a l’inici.
La pàgina es mou amb el dit i queda fixada en acabar el gest. També pots prémer
les etiquetes **Inici** i **Aplicacions →**, accessibles amb TalkBack o teclat.
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


## Nou inici v0.8

El mosaic ara mostra temps i agenda a l’esquerra, correu i notícies a la dreta
quan el mòbil és ample, i un resum amb desplaçament vertical en format compacte.
La barra fixa conté **Trucades · WhatsApp · Agenda**. Una pulsació llarga a Agenda
obre la selecció de calendaris. Tocar l’hora obre les alarmes de l’app Rellotge.
WhatsApp Business és l’alternativa si no hi ha WhatsApp normal al mateix perfil.

El fons inclòs és una il·lustració fotogràfica generada d’una costa mediterrània,
no una fotografia real de Cabrils. Les dades reals de Cabrils trien entre sol,
núvols, pluja i nit, amb partícules de pluja/neu i il·luminació. Amb dades de més
de dues hores o sense dades es torna al fons neutre. Una foto pròpia es pot
desenfocar i rep les capes meteorològiques i la llum de dia/nit si els efectes
sobre la foto estan activats. No es puja la foto a cap servidor.

**Correu** mostra exclusivament notificacions actives de Gmail/Outlook amb accés
opcional a notificacions d’Android. No consulta la safata d’entrada ni calcula
el total de correus sense llegir. Els textos i els PendingIntent viuen només en
memòria; es filtren altres aplicacions i resums de grup. Es pot ocultar la
previsualització o retirar l’accés a Configuració d’Android. Els comptes en un
altre perfil poden no estar disponibles. No es necessita compartir contrasenyes.

**Descobreix** usa RSS/Atom amb Xataka, Time Extension i VidaExtra per defecte.
Les fonts es poden canviar a Configuració → Fonts de notícies. Els titulars,
enllaços i miniatures provenen dels feeds; tocar una notícia obre el lloc
original al navegador. S’actualitzen com a màxim cada hora o en tocar ↻;
la memòria cau i l’hora d’actualització es mostren quan falla la xarxa.

Les compilacions debug de GitHub no tenen una clau de signatura persistent.
Si Android indica que la signatura és incompatible amb una versió anterior,
cal desinstal·lar-la: això esborra les dades locals, la configuració i el fons
importat de MagicFold. Les fotos originals no es modifiquen.


## Notícies i alarmes v0.9

Descobreix inclou **Tecnologia, Retro, Videojocs, Futbol, Economia, Política catalana,
Política espanyola i Món**. Llisca la fila de seccions o toca **Notícies · Seccions ▾**
per escollir-ne una. Es conserva la selecció en canviar de mida o reiniciar.
La fila de seccions es desplaça sense obrir la pàgina d’aplicacions.
Hi ha fins a 20 titulars per feed; **Veure tots els titulars** obre la llista completa.
L’inici mostra tres titulars en format compacte i cinc en format ample.
Món ressalta el primer titular i conserva l’ordre editorial del feed internacional;
no calcula una classificació pròpia de rellevància.

Fonts noves per defecte: Mundo Deportivo (futbol), ARA (economia i internacional),
Nació (política catalana) i elDiario.es (política espanyola). La secció política de
Nació també pot cobrir política estatal amb incidència a Catalunya. Les fonts
es poden personalitzar amb RSS/Atom HTTPS; els articles poden tenir subscripció
al lloc original. No es descarreguen articles complets ni s’eviten murs de pagament.
Les fonts configurades de les tres seccions anteriors es conserven.

Tocar l’hora demana **mostrar les alarmes**, sense crear-ne ni activar-ne cap.
S’afegeix el permís normal SET_ALARM per compatibilitat amb rellotges de fabricants.
Si l’activitat d’alarmes no existeix o el sistema en denega l’accés, es prova
l’entrada pública del rellotge d’Honor, Huawei, Android o Google. Si no hi ha
cap rellotge accessible, es mostra un avís i MagicFold continua obert.
La correcció es valida en emulador; encara cal provar-la al Magic V2 físic.


## Temes i perspectiva v0.12

**Configuració → Temes i perspectiva** ofereix Metro, Terminal (MS-DOS / Linux) i Cyberpunk · Night City.
Terminal fa servir text monoespaiat, fons CRT estàtic, finestres rectangulars
verdes, símbols meteorològics de text i accents ambre. No és un intèrpret de
comandes. Les mateixes dades i accions continuen disponibles. Les fotos i el
paisatge es recuperen en tornar a Metro; no s’esborra el fons importat.
Les miniatures de notícies s’oculten al tema Terminal.

**Perspectiva 3D** permet activar/desactivar el moviment i recalibrar el centre.
Un sensor de rotació amb fusió giroscòpica inclina els panells fins a 8 graus,
amb suavitzat i translació de fins a 8 dp per eix. S’adapta a la rotació de la pantalla i es
recalibra en reconstruir la vista després d’obrir/tancar el plegable. No mou
la barra de Trucades/WhatsApp/Agenda ni el rellotge, i es congela durant els
gestos tàctils. S’atura a la pàgina d’apps, en perdre el focus o pausar el
launcher, amb estalvi de bateria, amb els efectes generals desactivats o amb
les animacions d’Android desactivades. No requereix accés a notificacions.
Sense sensor de rotació compatible, el launcher continua estàtic.

Les proves verifiquen persistència i canvi de temes, controls principals,
redimensionament, límits i suavitzat del moviment i transformació real dels
píxels d’un panell. El comportament del giroscopi físic a Honor encara requereix
validació al dispositiu.


## Correcció de perspectiva v0.11

La 0.10 associava el sensor al ViewPager abans del primer layout: encara no
contenia les pàgines i podia registrar zero panells. Ara s’associa directament
al contingut d’inici ja construït. Una actualització de dades conserva la
inclinació i la calibració si la mida de finestra no ha canviat; obrir/tancar
el plegable o canviar l’orientació continua recalibrant quan correspon.

A **Temes i perspectiva → Perspectiva 3D** es mostra el sensor detectat, si
s’han rebut lectures i si la configuració bloqueja l’efecte (efectes visuals,
estalvi de bateria o animacions d’Android desactivades).

La prova de regressió injecta vectors de rotació al mateix processament que
utilitza el sensor i comprova que es mouen els panells reals de l’Activity,
que es congelen durant un toc i que mantenen la inclinació en reconstruir
la vista. No substitueix una prova del sensor físic al Honor.


## Profunditat, apps fixades i Cyberpunk v0.12

El moviment té més del doble de sensibilitat: 10° de moviment del mòbil
produeixen 4° d’inclinació, amb un límit de 8°. Les ombres natives de 10 dp
i l’espai al voltant dels panells reforcen la profunditat sense moure el dock.

**A MÀ** mostra totes les apps seleccionades, en tantes files com calgui.
S’ha eliminat el límit intern de quatre apps. La columna es desplaça verticalment
si la llista supera la pantalla; mantén premuda una app a la pàgina d’aplicacions
per afegir-la o un mosaic fixat per treure’l.

**Cyberpunk · Night City** afegeix superfícies violeta fosc, accents cian i magenta,
tipografia monoespaiada i una ciutat/reixeta neó estàtica sobre el paisatge
meteorològic o la foto triada. Conserva el temps i els efectes configurats.
Les proves cobreixen la selecció i persistència del tema, canvi de mida,
contrast, dock i accés a totes les apps; també verifiquen més d’una fila
d’apps fixades en vista compacta i ampla i la nova resposta del moviment.


## Tres temes amb composició pròpia v0.13

**Windows 10 Mobile**: fons negre, mosaics plans i rectangulars, tipografia
lleugera, apps fixades a la part superior i llistat d’apps agrupat per lletres.
És una recreació visual dins d’Android; no substitueix el sistema ni clona les
apps, la barra d’estat o el comportament intern de Windows Phone.

**macOS · Escriptori**: fons de cintes de llum amb moviment suau i tons segons
el temps, barra superior clara, finestres arrodonides amb els tres cercles
decoratius i dock centrat amb icones de colors i accés a la pàgina d’apps.
Els cercles de les finestres són decoratius, no controls de finestres Android.

**Monkey Island · Port pirata**: il·lustració original de port caribeny en pixel art,
marcs de fusta i llautó, text de quadern i dock d’inventari. El botó **Mapa**
obre el Mapa del capità: Port mostra el temps real de Cabrils, Quadern obre
l’agenda, Taverna obre els titulars i Missatges obre WhatsApp. La nit del port
és una elecció artística; les dades meteorològiques continuen sent reals.
No s’utilitzen fitxers ni personatges extrets del joc original.

Els tres temes conserven el rellotge/alarmes, calendaris, avisos de correu,
notícies, totes les apps fixades, el gest entre pàgines i la perspectiva del sensor.
Cada tema nou mostra primer el seu fons propi. Si hi importes una foto,
aquesta passa a ser el fons d’aquell tema. **Imatge de fons → Recupera el fons
d’aquest tema** restaura l’art o l’escriptori sense esborrar la foto importada.

La il·lustració està integrada a `app/src/foldable/res/drawable-nodpi/pirate_harbor.png`.
La procedència i el prompt de generació són a `docs/THEME_ART.md`.


## Escriptori lliure amb el mòbil obert v0.14

Als temes Windows i macOS, la vista ampla mostra finestres pròpies de
MagicFold: Temps, Agenda, Correu, Notícies i Apps fixades. Arrossega la barra
superior per moure-les i el cantó inferior per canviar-ne la mida. El botó
quadrat maximitza/restaura; el signe menys minimitza. Els panells minimitzats
es recuperen des de la franja inferior o **Panells**. **↺** restaura la distribució.
La finestra de notícies comença minimitzada per deixar espai als altres panells.
Les apps fixades es mouen com a grup dins de la seva finestra.

Les posicions, mides, ordre i estat dels panells es guarden per tema. En
tancar el mòbil torna la vista compacta; en obrir-lo es recupera l’escriptori.
La cerca i la pàgina d’aplicacions continuen a la dreta. Els gestos de les
barres i cantons no canvien de pàgina. Trucades i WhatsApp continuen al dock.

**Temes i perspectiva → Escriptori i apps** permet desactivar l’escriptori lliure
o la petició de finestres externes. MagicFold envia una mida suggerida a Android
amb `ActivityOptions.setLaunchBounds`; el sistema pot ignorar-la. No podem
forçar totes les altres apps a executar-se dins de finestres ni incrustar-les
dins del launcher amb els permisos normals d’una app. Si s’obren a pantalla
completa, utilitza el dock lateral de multifinestra de MagicOS per fer-les flotants.
Això depèn de la versió de MagicOS i de l’app. No s’ha verificat en un Honor real.

Fonts primàries:
- https://developer.android.com/reference/android/app/ActivityOptions#setLaunchBounds(android.graphics.Rect)
- https://www.honor.com/uk/support/content/en-us15881484/
