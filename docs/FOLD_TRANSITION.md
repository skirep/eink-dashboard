# MagicFold 0.16 · Transició en obrir i tancar

El canvi entre la vista compacta i l'escriptori desplegat té una transició
bidireccional de fusió, zoom subtil i difuminat progressiu. S'activa per defecte.
Configuració → Transició en plegar permet desactivar-la.

El contingut torna a quedar nítid al final. Un toc interromp l'efecte abans de
processar el gest. Els refrescos de dades esperen que acabi la transició.
L'efecte respecta Efectes visuals, l'estalvi de bateria i les animacions d'Android.
El difuminat utilitza RenderEffect a Android 12 o posterior; en versions anteriors
es manté la fusió i el zoom. No requereix cap permís addicional.

L'animació és dins de MagicFold i comença quan Android notifica el canvi de vista.
El traspàs físic de la pantalla exterior a la interior el gestiona MagicOS.
No és un efecte de vidre permanent sobre els panells ni una reproducció verificada
d'un vídeo de referència d'iPhone Duo.
