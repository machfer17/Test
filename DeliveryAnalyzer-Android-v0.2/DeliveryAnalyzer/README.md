# Delivery Analyzer — prototype Android (Samsung Galaxy S24)

## Fonctions
- Bouton flottant **ANALYSER** au-dessus des applications (via un service d'accessibilité que tu actives explicitement).
- Lecture **sur appui seulement** des textes accessibles de l'application affichée ; pas de capture d'écran, pas d'OCR et pas de surveillance continue.
- Extraction conservatrice d'une adresse visible, géocodage France par API Géoplateforme puis consultation OpenStreetMap via Overpass.
- Panneau montrant les observations sur les bâtiments et le stationnement. Les données publiques ne permettent pas de confirmer une place légale devant l'entrée.
- Test manuel d'une adresse dans l'application.

## Construire dans Android Studio
1. Installer Android Studio récent + Android SDK 35 et JDK 17.
2. Ouvrir le dossier **DeliveryAnalyzer** (File > Open), pas le dossier `app`.
3. Laisser Gradle synchroniser le projet (connexion Internet nécessaire). Android Studio peut proposer d'installer le SDK 35.
4. Connecter le Galaxy S24 par câble (options développeur > Débogage USB), puis cliquer Run ; ou générer un APK dans Build > Build APK(s).
5. Ouvrir l'application et tester une adresse dans le champ.
6. Appuyer sur « Ouvrir les paramètres d'accessibilité », puis activer **Delivery Analyzer** dans les applications installées. Un panneau flottant apparaîtra.
7. Ouvrir Uber Driver avec une offre à l'écran et appuyer sur **ANALYSER**. Pour déplacer le panneau, toucher son espace vide et le glisser ; `×` masque seulement le texte du résultat.
8. Pour arrêter complètement le panneau, désactiver le service dans les paramètres d'accessibilité.

## Limitations importantes
- Android doit exposer le texte dans son arbre d'accessibilité. Si Uber dessine l'offre comme une image, ce prototype ne peut pas lire l'adresse. Aucun contournement n'est inclus.
- Uber peut ne pas afficher l'adresse exacte avant acceptation ; l'application ne devine pas les adresses masquées.
- La rue + ville (de préférence code postal) doivent être visibles pour la détection ; le bouton peut renvoyer « Adresse non détectée ».
- Les bâtiments OSM sont examinés dans un rayon de 65 m, sans appariement cadastral au numéro : **ne jamais interpréter une classe OSM voisine comme la catégorie certifiée de l'adresse**.
- Stationnement : seul le signalement OSM à proximité est montré ; cela ne prouve ni autorisation, ni disponibilité. Ne jamais stationner devant des accès privés, sur les trottoirs ou en double file.
- Les services publics peuvent être intermittents / limiter les requêtes ; aucun délai maximal réel de réponse n'est garanti.
- Les adresses soumises sont envoyées à Géoplateforme et à l'instance Overpass interrogée ; aucun serveur propre à Delivery Analyzer n'est utilisé.
- Ne manipuler l'outil qu'une fois le véhicule immobilisé.
- Un usage de services d'accessibilité à des fins autres que l'assistance peut être soumis à des règles de publication spécifiques (Google Play) ; vérifier aussi les conditions applicables à l'application affichée.

## Architecture
- `MainActivity.kt` : écran d'installation et test manuel.
- `AnalyzerAccessibilityService.kt` : panneau flottant et lecture de l'écran après un clic.
- `DeliveryEngine.kt` : extraction d'adresse, géocodage, requêtes OSM, synthèse prudente.

## Évolution V2
- Ajout d'une vraie capture d'écran après consentement MediaProjection + OCR local ML Kit pour les interfaces qui n'exposent pas leur texte.
- Requête aux bâtiments proches appariés à l'adresse, BDNB si disponible.
- Données de stationnement communales, restrictions de voirie, cache local, historique de livraison.

Aucun APK n'est inclus : ce projet source doit être compilé avec Android Studio. Cette livraison n'a pas été compilée/testée physiquement sur le S24.

## V0.2 : sélection de l'adresse client dans l'offre Uber Eats
- Le lecteur conserve les positions verticales des textes de l'écran.
- Il repère **Accepter**, puis cherche **la dernière adresse de rue au-dessus** de ce bouton.
- Il concatène jusqu'à deux lignes suivantes pour inclure code postal et ville coupée (ex. `Cesson-` + `Sévigné`).
- Le nom du restaurant est ignoré si, comme dans la capture fournie, il précède l'adresse de livraison.
- **Sécurité** : en l'absence de bouton Accepter ou d'adresse complète avec code postal et ville, aucune analyse n'est lancée.
- Ce comportement correspond à la disposition décrite par l'utilisateur ; toute modification d'interface d'Uber peut nécessiter une adaptation.
- Le système dépend toujours du texte rendu accessible par Uber. Il **n'inclut pas d'OCR**.
