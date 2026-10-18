# Fold Metro

Launcher Android natiu, inspirat en els mosaics de Windows 10 Mobile. Variant
`foldable` del projecte e-ink existent, amb identificador independent
`com.eink.launcher.foldable`; es poden tenir les dues apps instal·lades.

## Ús

1. Instal·la l'APK **Fold-Metro-Android** de l'última execució correcta de
   [Fold Metro APK](https://github.com/skirep/eink-dashboard/actions/workflows/foldable.yml).
2. Obre **Fold Metro** i toca **Estableix com a inici**.
3. **Connecta l'agenda** demana només lectura. Mostra els calendaris visibles
   sincronitzats al dispositiu, recurrències incloses, dels pròxims set dies.
   No llegeix calendaris remots que Android no tingui sincronitzats.
4. **⚙ → Ciutat del temps**: cerca i confirma la ubicació. Cabrils és la inicial.
5. Toca un mosaic per obrir l'app. Pulsació llarga per treure'l o fixar-lo
   des de **Totes les aplicacions**. La cerca i els preferits són locals.

## Pantalles

- Compacta: temps resumit, dos pròxims actes i mosaics en dues o tres columnes.
- Ampla: temps amb previsió de cinc dies i apps a l'esquerra; agenda a la dreta.
- Frontissa separadora: panells desplaçables independents que reserven el plec,
  també en orientació de taula. Jetpack WindowManager observa els canvis.
- Si el fabricant no exposa `FoldingFeature`, s'adapta a partir de 600 dp
  d'amplada i ho indica com a **adaptació per mida**. Una finestra ampla no
  prova que la frontissa estigui físicament oberta.
- Canvis de mida, rotació i recreació conserven cerca, vista i desplaçament.

## Temps i privadesa

Open-Meteo, sense clau API, amb atribució visible. La consulta envia coordenades
de la ciutat triada al servei; no envia l'agenda. No utilitza GPS ni serveis de
Google. Actualitza cada 30 minuts mentre el launcher és visible, amb reintent
manual i dades locals amb hora d'actualització quan falla la xarxa.
La ciutat trobada requereix confirmació per evitar triar un homònim en silenci.
El servei gratuït té condicions d'ús no comercial: https://open-meteo.com/en/terms

## Compilació i verificació

JDK 17 i Android SDK 34:

```sh
./gradlew testFoldableDebugUnitTest assembleFoldableDebug lintFoldableDebug assembleEinkDebug
```

APK: `app/build/outputs/apk/foldable/debug/app-foldable-debug.apk`.
Variant original: `./gradlew assembleEinkDebug`.
El workflow comprova totes dues variants i publica l'APK i informes.

Cal verificar en un plegable físic (en particular Honor Magic V2):
obrir/tancar diverses vegades des d'inici i des de la cerca, rotació,
font gran, inici per defecte i retorn des d'una app, calendari denegat/revocat,
esdeveniments recurrents i de tot el dia, xarxa desconnectada, ciutat canviada,
instal·lació/desinstal·lació d'apps i persistència de mosaics.
No s'ha de confondre una compilació correcta amb aquesta validació física.

Documentació base:
- https://developer.android.com/develop/ui/views/layout/foldables
- https://developer.android.com/reference/android/provider/CalendarContract.Instances
- https://open-meteo.com/en/docs
