---
title: "Rapport final de readiness et de démarrage"
subtitle: "Timeline agentique — application Android locale et gratuite"
author: "Rapport de décision produit et technique"
date: "5 novembre 2026"
lang: fr-FR
---

**Version :** 1.0  
**Date :** 5 novembre 2026  
**Produit :** application Android native, sunnite (quatre madhahib), arabe et anglais  
**Modèle d'exploitation :** gratuite, sans publicité, sans achat intégré, sans serveur applicatif  
**Référence d'architecture :** *Cahier d'architecture Timeline agentique v1.2*

---

\newpage

# Décision exécutive

# GO pour commencer le développement

# NO-GO pour publier une fonction religieuse ou IA comme fiable avant ses portes de validation

Le cahier v1.2 est suffisant pour commencer le dépôt, l'architecture Android, les contrats Kotlin, les migrations locales, la Timeline, le moteur d'autorisation, la persistance agentique, l'import SAF et le pipeline RAG derrière des interfaces.

Il n'est pas nécessaire de retarder le premier commit en attendant que tous les modèles, tous les corpus, toutes les licences et tous les moteurs métier soient définitivement sélectionnés. Ces travaux doivent avancer en parallèle.

La règle de conduite est la suivante : **un point non résolu bloque le module ou le canal de distribution qui en dépend, pas le développement de toute l'application.**

Le produit peut être développé avec une architecture complète et des fonctionnalités progressivement activées. En revanche, aucune réponse religieuse ne doit être présentée comme validée tant que le corpus, les citations, les règles et la revue spécialisée correspondants ne sont pas approuvés.

### Décisions finales à appliquer dès le premier commit

| Sujet | Décision pour le développement |
|---|---|
| Plateforme | Android natif, Kotlin, Jetpack Compose |
| Périmètre religieux | Sunnite, quatre madhahib ; aucun madhhab présélectionné |
| Langues initiales | Arabe et anglais ; identifiants BCP-47 et architecture extensible |
| Distribution de l'application | Google Play envisagé ; APK interne de test via CI |
| Gratuité | Application destinée à rester gratuite sur son identifiant/package Play initial |
| Monétisation initiale | Aucune publicité, aucun SDK publicitaire, aucun IAP, aucune facturation, aucun don intégré |
| Serveur | Aucun backend applicatif, compte, API produit, stockage cloud, analytics ou télémétrie |
| IA | On-device uniquement ; modèle facultatif, sélectionné et installé explicitement |
| Import des modèles/corpus | Storage Access Framework (SAF) comme voie obligatoire de secours et première voie sûre |
| Play AI Packs | Spike séparé ; ne pas dépendre du sharding avant preuve sur Play interne |
| Persistance | Room ; chiffrement de la base privée après spike SQLCipher |
| RAG initial | FTS5/BM25 + recherche vectorielle derrière `VectorIndexPort` + RRF ; reranker conditionnel |
| Signature des corpus | Clé privée de publication hors de l'application ; l'application vérifie avec clé publique |
| Min SDK proposé | API 26, décision de départ à confirmer par matrice d'appareils avant release 1.0 |
| Target SDK Google Play | API 36 pour toute nouvelle application ou mise à jour soumise après le 31 août 2026 [S01] |
| CI/CD | GitHub Actions ; aucune donnée utilisateur ni modèle volumineux dans les artefacts ordinaires de CI |

---

# 1. Revue critique du rapport de readiness

Le rapport pose la bonne question, mais son verdict « pas encore prêt à coder » est trop restrictif. Il confond parfois « prêt à développer » avec « prêt à publier ». Il identifie de vrais risques, mais certains sont déjà traités dans v1.2 et d'autres doivent être corrigés.

## 1.1 Tableau de verdict

| Affirmation du rapport | Verdict | Décision finale |
|---|---|---|
| Le cahier v1.2 n'est pas un feu vert pour publier l'application complète | **CORRECT** | Une architecture n'est pas une preuve de qualité, de sécurité ou de validation savante. |
| On ne peut pas encore commencer à coder | **NOT CORRECT** | On peut commencer les fondations et les modules non dépendants des spikes. |
| Squelette Gradle multi-modules et frontières de dépendances | **CORRECT** | Commencer immédiatement. |
| CI GitHub Actions avec lint, tests, manifeste et SBOM | **CORRECT** | Commencer immédiatement ; ajouter contrôle des dépendances et des permissions fusionnées. |
| Tables Room utilisateur et tests de reprise | **PARTIAL** | Commencer le schéma initial, mais ne pas figer toutes les tables sans migrations testées ; le chiffrement doit être validé avant les données sensibles de production. |
| Moteurs prière et Zakat déterministes à coder maintenant | **PARTIAL** | Les API, types, calculs génériques et tests mathématiques peuvent commencer ; les règles religieuses finales doivent être documentées et approuvées par des spécialistes. |
| Fusion RRF et normalisation arabe | **CORRECT** | Implémenter avec jeux de tests linguistiques ; garder texte original et champs normalisés séparés. |
| Contrats Kotlin (`RetrievalPort`, `EmbeddingProvider`, `ActionProposal`, etc.) | **CORRECT** | Ils sont suffisamment spécifiés dans v1.2 pour être codés. |
| Spike Play AI Packs avant toute implémentation | **PARTIAL** | Nécessaire uniquement si l'on veut activer cette distribution. SAF permet de développer et de tester le Model Manager sans attendre. |
| Découper E2B en plusieurs AI Packs est une solution connue | **NOT CORRECT** | Le sharding/réassemblage n'est pas documenté comme workflow garanti par Google. Il reste une hypothèse à prouver. |
| Spike SQLCipher + Room obligatoire | **PARTIAL** | Un spike d'intégration est nécessaire, mais l'incertitude n'est pas une incompatibilité conceptuelle : SQLCipher documente Room 2 via `SupportOpenHelperFactory`. |
| Spike Ed25519 Android Keystore obligatoire | **NOT CORRECT** | L'application ne signe pas les packs : elle vérifie une signature avec une clé publique. La clé privée de publication ne doit jamais être embarquée. Il faut tester une implémentation de vérification sur les API supportées. |
| E4B a un coût mémoire important | **CORRECT** | Un preflight et des tests sur appareils physiques sont indispensables. |
| E4B exige environ 7 Go de RAM totale | **NOT CORRECT** | Aucun seuil universel ne peut être déduit des benchmarks publiés. La mémoire dépend du modèle exact, du backend, du contexte et de l'appareil. |
| Le RAG n'est pas spécifié dans v1.2 | **NOT CORRECT** | Les sections 6 et 7 de v1.2 détaillent déjà chunking, métadonnées, retrieval, RRF, reranking conditionnel et validation des citations. Le travail restant est l'implémentation et l'évaluation empirique. |
| Le reranking doit être étudié pour mobile | **CORRECT** | Il est conditionnel ; RRF seul est le mode de repli prévu. |
| La persistance agentique est absente | **PARTIAL** | v1.2 spécifie checkpoints, leases, event log, outbox, états et reprise. Il faut les implémenter et tester par crash-injection. |
| Aucun serveur est incompatible avec les données live | **CORRECT** | Les données live ne sont pas promises. Les cartes doivent afficher source, heure et statut de fraîcheur. |
| Gratuit sans annonces signifie automatiquement non-commercial | **NOT CORRECT** | C'est un élément favorable, pas une conclusion juridique automatique au regard des licences NC. |
| OpenITI peut être intégré parce que l'application est gratuite | **PARTIAL** | Licence NC-SA déclarée, mais chaque élément source, les adaptations, les index et les conditions de redistribution doivent être audités. |
| Sunnah.com permet l'utilisation gratuite de collections entières en application offline | **NOT CORRECT** | Les conditions officielles interdisent scraping et reproduction massive ; l'API ne remplace pas un dump offline autorisé. |
| Tanzil arabe peut être utilisé avec attribution | **CORRECT** | Copie verbatim permise sous conditions ; le texte ne doit pas être modifié. Les traductions sont à examiner individuellement. |
| On doit attendre toutes les réponses avant de coder | **NOT CORRECT** | Les spikes, la gouvernance documentaire et le développement doivent avancer en parallèle. |

## 1.2 Les quatre spikes du rapport — portée exacte

### Spike A — Distribution Play AI Packs

**À réaliser, mais non bloquant pour l'ensemble du développement.**

La documentation Play for On-device AI (bêta) indique une limite de 1,5 Go par AI Pack sur la taille compressée téléchargée et 4 Go cumulés pour la version d'application générée par l'App Bundle. Les AI Packs contiennent des modèles, pas les bibliothèques Java/Kotlin, les bibliothèques natives ou les bases de corpus. [S02]

Les fichiers LiteRT-LM publiés sont environ 2,58 Go pour E2B et 3,65 Go pour E4B. Ces tailles de fichier ne permettent pas à elles seules de déterminer leur taille compressée finale dans un pack. Il faut mesurer l'artefact réellement envoyé à Play. [S03]

Le découpage en fragments, leur livraison séparée et le réassemblage du modèle constituent une hypothèse non garantie. Le spike doit prouver le téléchargement, la reprise, l'assemblage en streaming, le hash final, le chargement LiteRT-LM, la mise à jour et le rollback dans une piste Play interne.

**Décision de développement :** l'import SAF est obligatoire et constitue le chemin de secours. E2B et E4B sont importés par SAF tant que le spike Play n'est pas vert. Le Model Manager ne dépend donc pas de ce spike.

### Spike B — SQLCipher + Room

**À réaliser pendant la construction du stockage.**

SQLCipher Android documente Room 2 via `SupportOpenHelperFactory` et Room 3 via son driver. Le rapport ne doit donc pas présenter Room 2 comme non pris en charge. La compatibilité exacte de nos versions, ABI, migrations et clés reste à tester. [S04]

**Base de spike recommandée :** Room 2.8.5 + SQLCipher Android 4.20.0 + AndroidX SQLite 2.7.0, puis essai distinct de SQLite 2.7.1 si désiré. Cette combinaison est un candidat, pas une déclaration de compatibilité déjà acquise.

Tests obligatoires : création, ouverture, fermeture, migrations, kill brutal, redémarrage, récupération de clé, corruption, backup, restauration, ARM64, absence de fuite de secret dans Logcat.

Ne pas enregistrer des données privées de production avant succès du spike. Le code métier peut être construit contre `UserDataStore` sans dépendre du fournisseur SQL concret.

### Spike C — Vérification des signatures Ed25519

**Le problème doit être reformulé.**

La clé privée qui signe les Corpus Packs est conservée hors de l'application, idéalement dans un environnement de publication isolé. L'application embarque une ou plusieurs clés publiques et vérifie les signatures. Elle n'a aucune raison de générer ou stocker la clé privée Ed25519 dans Android Keystore.

La documentation Android liste `Ed25519` pour `Signature` à partir d'API 33 ; une application ciblant API 26 doit donc vérifier la compatibilité en dessous de 33 avec une bibliothèque/provider pris en charge et figé. [S05]

**Spike recommandé :** tester un vérificateur Ed25519 éprouvé, par exemple le chemin Tink Android ou un autre fournisseur audité, avec les vecteurs de test officiels sur API 26, 29, 33 et 36. Tester aussi les clés de rotation, la révocation et la signature invalide. Aucun secret de signature n'entre dans l'APK ou le dépôt Git.

### Spike D — Mémoire E4B

**À réaliser avant d'annoncer la compatibilité E4B, mais pas avant de créer le Model Manager.**

Google publie pour E4B sur Galaxy S26 Ultra des mesures distinctes selon backend : mémoire de processus CPU autour de 3 283 Mo et valeur GPU publiée autour de 710 Mo, avec différentes latences et débits. Ces valeurs sont propres au benchmark et ne constituent pas un seuil de RAM totale universel. [S03]

Le ModelPreflight doit mesurer au minimum : RAM disponible au moment du chargement, PSS du processus, stockage libre et temporaire, ABI, version Android, modèle exact, quantification, runtime, backend réellement chargé, TTFT, tokens/s, crash, température et stabilité sous pression mémoire.

Le système décide `READY`, `CONSTRAINED` ou `UNAVAILABLE` d'après des seuils dérivés de tests répétés sur appareils réels. Aucune décision basée seulement sur le chiffre commercial « 8 Go RAM ».

---

# 2. Gratuit aujourd'hui, éventuellement payant demain : décision structurante

## 2.1 Recommandation

**Publier l'application principale comme gratuite de façon permanente sur Google Play.**

Ne pas ajouter aujourd'hui Google Play Billing, annonces, abonnement, donations intégrées, analytics publicitaires ni système d'entitlements. Aucun module de monétisation n'est nécessaire à l'architecture actuelle.

Cette recommandation correspond à la probabilité indiquée par le propriétaire du produit : application gratuite très probable, éventuelle version payante peu probable.

## 2.2 Conséquence Google Play à connaître

Google Play permet de transformer une application payante en gratuite. En revanche, une application qui a déjà été proposée gratuitement ne peut pas ensuite devenir payante sous le même package name. Pour la vendre comme application payante, Google demande de créer une nouvelle application avec un nouveau package name. [S06]

Par conséquent :

- le package initial reste gratuit ;
- si une version payante est décidée plus tard, elle sera une application sœur avec un nouvel `applicationId` ;
- les modules métier peuvent partager le même code source ;
- les fonctionnalités payantes, si elles existent un jour, seront définies dans cette application distincte ;
- un export/import chiffré, local et volontaire permettra de transférer les données entre les deux applications ;
- aucune dépendance Billing ne sera ajoutée au code commun aujourd'hui.

Il faut réserver et figer le vrai package name avant le premier lancement public Play, mais le squelette et les tests internes peuvent démarrer avec un identifiant provisoire.

## 2.3 Architecture à préserver pour une version sœur

Ne pas mélanger code, données et licences de corpus sous une condition globale `isPaid`.

Le gestionnaire de corpus doit pouvoir installer/retirer un pack selon son identifiant de licence et le profil de distribution. Une future application payante ne doit pas hériter automatiquement des collections CC-NC présentes dans l'application gratuite.

Format d'export utilisateur : versionné, chiffré, authentifié, documenté, déclenché par l'utilisateur, sans données de modèle ou de corpus redistribuables. Il contient seulement les données personnelles que l'utilisateur choisit d'exporter.

---

# 3. Licences : décision compatible avec la gratuité et une éventuelle version payante

## 3.1 « Gratuit » n'est pas automatiquement « non-commercial »

Creative Commons définit NC comme une utilisation qui n'est pas principalement destinée à, ou dirigée vers, un avantage commercial ou une rémunération monétaire. Creative Commons précise que la qualification dépend des circonstances et de l'intention, pas simplement du statut juridique de l'utilisateur. Une application gratuite, sans ads, sans IAP, sans dons intégrés et sans monétisation des données est un contexte favorable à une utilisation non commerciale, mais ce n'est pas une autorisation juridique automatique. [S07]

**Décision :** le projet ne s'appuiera pas sur une interprétation optimiste de NC pour constituer son corpus central.

## 3.2 Politique de licence du produit

Le corpus central livré avec toutes les variantes doit être constitué de contenus dont la redistribution par l'application, la reproduction locale, les adaptations nécessaires et, lorsque requis, les index/embeddings sont autorisés par écrit ou par licence vérifiée.

| Source/corpus | État | Décision produit |
|---|---|---|
| Texte arabe Tanzil | CC BY 3.0 déclarée ; copie verbatim permise avec attribution, lien et avis de copyright ; modification interdite | Candidat pour le noyau, mais garder le texte exact immuable. Faire vérifier la distribution des index/embeddings dérivés. [S08] |
| Traductions Tanzil | Conditions variables ; la page de traductions indique un usage non commercial pour les traductions qu'elle héberge et demande une permission du traducteur/éditeur pour l'usage commercial | Audit traduction par traduction ; aucune traduction n'est incluse par simple héritage de la licence du texte arabe. [S09] |
| OpenITI | Releases publiées sous CC BY-NC-SA 4.0 ; fichiers issus de bibliothèques numériques différentes et qualité documentaire variable | Pas dans le corpus central partagé. Pack complémentaire isolé seulement après audit par fichier, contrôle qualité et avis juridique sur l'usage non commercial et les dérivés. Exclure de toute future version payante sans permission commerciale. [S10] |
| Sunnah.com | Interdit scraping et reproduction massive de livres/collections ; reproduction de hadiths individuels ou sélections à des fins pédagogiques permise ; l'API n'expose qu'une partie des données et le dump offline n'est pas disponible dans la documentation consultée | Ne pas scraper ni redistribuer les collections. Demander une autorisation écrite pour un corpus offline complet ou sélectionner une autre édition/source dont les droits permettent la redistribution. [S11][S12] |
| IslamicRAG-7M | Licence de dataset annoncée permissive, mais provenance et droits sous-jacents de chaque texte/traduction/embedding non prouvés | Candidat de recherche et de benchmark uniquement ; aucun pack distribué avant audit ligne/collection et droits sur dérivés. |
| Sources de fiqh, tafsir, cours et traductions | Droits variables selon auteur, éditeur, édition, traduction et format | Une entrée de registre par document/édition/asset ; pas de redistribution si licence inconnue. |

### 3.3 Registre de droits obligatoire

Chaque asset doit avoir des champs vérifiables :

- source et propriétaire/éditeur ;
- document, édition et version exacte ;
- langue, traducteur et territoire si pertinent ;
- licence et copie du texte juridique/autorisation ;
- droit de redistribution dans une application ;
- droit de modification/normalisation ;
- droit de créer et distribuer des embeddings/index dérivés ;
- droit de redistribution commerciale et non commerciale ;
- obligations BY, SA, ND, attribution et lien ;
- statut de revue juridique ;
- approbateur et date.

Valeurs possibles de `licenseStatus` : `UNKNOWN`, `RESEARCH_ONLY`, `NONCOMMERCIAL_ONLY`, `COMMERCIAL_ALLOWED`, `PERMISSION_REQUIRED`, `NO_REDISTRIBUTION`, `REVOKED`.

La CI doit échouer si un corpus destiné à la release contient une entrée `UNKNOWN`, `RESEARCH_ONLY`, `PERMISSION_REQUIRED`, `NO_REDISTRIBUTION` ou `REVOKED`.

### 3.4 Séparation technique des packs

- Packs `CORE`: redistribution autorisée dans toutes les variantes visées.
- Packs `NC_ONLY`: jamais requis pour les fonctions centrales ; isolés, avec licence et attribution propres ; exclus d'une future application payante sauf autorisation écrite.
- Packs `USER_IMPORTED`: fichiers choisis par l'utilisateur, mais cela ne signifie pas que l'application peut redistribuer ces contenus ou les mélanger dans un corpus publié.

Les embeddings et index dérivés d'un texte héritent d'un statut de revue juridique propre. Ne pas supposer qu'un vecteur n'est jamais une adaptation ou une redistribution ; demander un avis si le cas est incertain.

**BLOQUANT de release :** les droits sur chaque source réellement livrée doivent être approuvés. Cela ne bloque pas le code du corpus manager, du parser, du moteur de recherche ou du format de pack.

---

# 4. Décisions techniques à figer pour commencer

## 4.1 Baseline Android

| Élément | Décision de départ | Motif |
|---|---|---|
| Langage | Kotlin | Contrats null-safe et intégration Android |
| UI | Jetpack Compose, Material 3 | RTL, état déclaratif, composants adaptatifs |
| Architecture | Single Activity, ViewModel, Coroutines/Flow, repositories | État persistant séparé de l'UI |
| minSdk | **26 (Android 8.0)**, à revoir seulement si la matrice de support révèle un besoin commercial concret | Compromis de départ ; API 26 permet d'éviter une partie de l'ancien parc sans prétendre que les modèles y fonctionneront tous |
| compileSdk | 36 | Cohérent avec la cible actuelle |
| targetSdk | **36** | Depuis le 31 août 2026, les nouvelles applications et mises à jour Google Play doivent cibler API 36 ou plus. [S01] |
| ABI de release | `arm64-v8a` en priorité ; `x86_64` pour émulateurs/tests | Cible principale des runtimes et modèles mobiles ; ajouter d'autres ABI uniquement après validation native |
| Build JDK | JDK 17 | Requis par la baseline AGP retenue ; verrouiller la toolchain CI et locale |
| Gradle Wrapper | **9.5.0** | Minimum requis par AGP 9.3.x selon la matrice Android ; version exacte verrouillée dans le wrapper. [S21] |
| AGP/Kotlin | AGP 9.3.1 / Kotlin 2.4.21 comme snapshot de départ | Kotlin 2.4.21 prend en charge AGP jusqu’à 9.3.1 ; vérifier le build complet dans CI avant de déclarer la combinaison figée. [S22] |

La valeur `minSdk 26` est une décision recommandée pour démarrer, pas une garantie de performance IA. Les fonctions non-IA restent indépendantes du modèle ; l'installation de E2B/E4B sera filtrée par le preflight matériel.

## 4.2 Stockage local

- Room 2.8.5 pour les données structurées.
- SQLCipher Android 4.20.0 comme candidat d'encryption provider ; valider `SupportOpenHelperFactory` avec Room 2.
- AndroidX SQLite 2.7.0 comme point de départ d'intégration ; tester 2.7.1 séparément avant upgrade.
- Android Keystore pour protéger la clé de base ou la clé d'enveloppement locale ; jamais une clé commune codée en dur.
- Base privée chiffrée séparée du corpus public en lecture seule.
- Exclusion des données sensibles des sauvegardes automatiques ; export chiffré et explicite.

SQLCipher supporte officiellement l'intégration Room 2 via `SupportOpenHelperFactory` et Room 3 via son driver. Le spike porte sur la combinaison réelle de versions et les migrations, pas sur l'existence d'une intégration Room 2. [S04]

## 4.3 Runtime IA

- `ModelProvider` abstrait.
- LiteRT-LM Kotlin comme runtime candidat pour Gemma 4 E2B/E4B.
- EmbeddingGemma text-only comme candidat initial pour retrieval ; ne pas le confondre avec la version multimodale.
- Android AI Core peut être évalué comme provider distinct sur appareils compatibles, mais ne garantit pas le même modèle, la même version ou la même disponibilité.
- Tool calling en mode manuel obligatoire : désactiver l'exécution automatique des tool calls. Une proposition de tool doit passer par `AuthorizationEngine` avant tout appel d'action.
- Modèle absent ou fermé : l'app reste utilisable ; pas de blocage de la Timeline.

### ModelPreflight

Le gestionnaire de modèle décide d'après :

- modèle, quantification, version et hash exacts ;
- RAM disponible, PSS et pression mémoire au moment du test ;
- espace disque final et temporaire ;
- API Android, ABI et appareil ;
- backend réellement chargé (CPU/GPU/NPU selon runtime) ;
- temps de chargement, TTFT et débit ;
- stabilité, température et batterie.

Statuts : `READY`, `CONSTRAINED`, `UNAVAILABLE`, `NOT_INSTALLED`, `CORRUPT`, `LICENSE_BLOCKED`.

Aucun seuil RAM unique n'est accepté comme preuve de compatibilité E4B.

## 4.4 Modèles et distribution

**Voie de référence : SAF import.** L'utilisateur choisit un fichier avec le sélecteur système. L'application copie en flux vers un espace de staging privé, vérifie taille/hash/format/licence/modèle, exécute un smoke test, puis active atomiquement. Annulation et reprise ne doivent jamais laisser un modèle partiellement actif. Le Storage Access Framework permet des URI persistantes si l'application doit conserver l'accès au document ; si le fichier est copié dans l'espace privé, la permission externe peut être relâchée. [S13]

**Voie Play AI Packs :** facultative, seulement après spike réussi dans une piste interne. Limites documentées : 1,5 Go compressé par AI Pack et 4 Go cumulés. AI Packs contiennent les modèles uniquement, pas les libs ni les corpus. [S02]

**GitHub Actions/Release ne sont pas la distribution des modèles.** Ne pas archiver E2B/E4B comme artefacts de build ordinaires. Hugging Face peut être indiqué comme lieu externe de téléchargement, mais l'application n'intègre pas de downloader et n'envoie aucune donnée à ce service.

## 4.5 Recherche locale

- SQLite FTS5/BM25 pour le lexical.
- Résolution exacte par index B-tree pour ayah, collection, édition, référence.
- `VectorIndexPort` pour la dense.
- Recherche vectorielle exacte comme baseline mesurable ; sqlite-vec n'est pas HNSW.
- RRF dans la couche retrieval.
- HNSW seulement si un benchmark sur corpus cible prouve un gain justifiant licence, mémoire, maintenance et taille.
- Cross-encoder non sélectionné au démarrage ; l'interface est codée maintenant.
- RRF-only fonctionne toujours. Le reranker ne tourne que si budget mémoire/thermique disponible et si le gold set démontre un gain.

## 4.6 Signature des Corpus Packs

- Signature de pack : Ed25519.
- Clé privée : hors application, jamais dans le dépôt ni l'APK ; idéalement hors ligne ou dans un environnement de signature protégé.
- Application : vérifie uniquement avec les clés publiques embarquées.
- Implémentation : `SignatureVerifier` abstraite ; Tink Android 1.24.0 est un candidat concret (prise en charge complète annoncée à partir d’API 24 ; Ed25519 listé pour Java). Tester ce provider sur API 26, 29, 33 et 36 avec vecteurs de test avant de le figer. [S19][S20]
- Signature et hash sont des contrôles distincts : un hash sans signature authentique ne prouve pas l'auteur du pack.
- Prévoir rotation, révocation, `keyId`, période de validité et procédure de récupération si une clé de publication est compromise.

## 4.7 OCR et ASR — ne pas choisir un moteur par supposition

Google ML Kit Text Recognition v2 ne documente pas l'arabe parmi les scripts/langues OCR pris en charge dans sa table actuelle. ML Kit Language Identification reconnaît l'arabe, mais l'identification de langue n'est pas de l'OCR. [S14]

Conséquences :

- OCR anglais : candidat ML Kit ; benchmark et licence à vérifier.
- OCR arabe étiquette/texte courant : comparer PaddleOCR et autres candidats sur photos réelles, appareils réels et licence des modèles.
- Photo de Mushaf : pipeline spécialisé de segmentation, alignement avec texte coranique canonique, vérification de l'ordre des ayahs et correction utilisateur. OCR générique ou Gemma seul ne peut pas certifier une transcription coranique.
- Audio : ASR et évaluation du tajwid sont deux problèmes différents. Gemma multimodal ne doit pas être présenté comme évaluateur tajwid validé.
- Les originaux photo/audio sont temporaires par défaut ; conservation uniquement sur demande explicite.

---

# 5. RAG : ce qui est déjà spécifié et ce qui reste à faire

## 5.1 Ce qui existe déjà dans v1.2

Les sections 6 et 7 de v1.2 spécifient déjà :

- chunking par ayah/groupe contigu pour le Coran ;
- hadith complet avec isnad, matn et variantes séparées ;
- tafsir par passage lié aux ayahs et parent ;
- fiqh par avis/argument avec conditions, exceptions et désaccords ;
- métadonnées de provenance, édition, école, langue, licence et statut de revue ;
- séparation entre texte original et normalisation de recherche ;
- exact reference retrieval, FTS5/BM25, dense, RRF ;
- reranking conditionnel et fallback ;
- construction d'un `EvidenceBundle` ;
- génération structurée de claims et validation des citations.

La critique « RAG pas spécifié » est donc **incorrecte pour le document v1.2**. Le vrai travail manquant est de transformer ces contrats en code, jeux de test et corpus validés.

## 5.2 Pipeline d'exécution à implémenter

1. Parser la requête : langue, domaine, références exactes, école éventuellement demandée.
2. Résoudre les références exactes.
3. Lancer FTS5/BM25 et dense en parallèle.
4. Fusionner les rangs par RRF.
5. Appliquer les filtres durs : périmètre sunnite, licence, intégrité, publication.
6. Appliquer le madhhab comme préférence/contrainte interprétative selon la question ; ne pas masquer les sources primaires communes ou désaccords pertinents.
7. Déclencher le cross-encoder seulement si la requête, le budget appareil et le gold set le justifient.
8. Charger contexte parent, conditions, variantes et références nécessaires.
9. Détecter couverture insuffisante, doublons et divergences.
10. Construire un EvidenceBundle avec IDs immuables et hashes.
11. Générer des claims structurés ; seules les preuves du bundle peuvent être référencées.
12. Valider chaque citation avant affichage ; sinon corriger, réduire l'affirmation ou s'abstenir.

## 5.3 Règle d'abstention

Aucune réponse religieuse factuelle ne doit être présentée comme sourcée si aucune preuve adéquate n'a été retrouvée et validée. L'absence de résultat dans l'index ne démontre pas qu'aucun avis ou texte n'existe.

Le système distingue :

- pertinence de retrieval ;
- provenance bibliographique ;
- revue documentaire ;
- classification attribuée par un spécialiste ;
- support d'un claim précis par un passage ;
- divergence entre écoles ;
- applicabilité au cas utilisateur.

Un score cosine, BM25, reranker ou « confiance du LLM » n'est jamais un niveau de vérité religieuse.

## 5.4 Jeu d'évaluation à construire

Avant d'activer les réponses religieuses, constituer un jeu de référence arabe/anglais, avec cas des quatre écoles et cas d'abstention. Pour chaque question : passages attendus, passages non pertinents, référence exacte, limites de réponse, divergences à montrer et réponse d'abstention attendue.

Mesurer : Recall@K, MRR/NDCG, exactitude des références, taux de citation exacte, taux de claims supportés, abstention correcte, latence P50/P95, RAM, stockage et température.

Le reranker n'est retenu que si ses gains sont mesurés sur ce jeu, pas sur un benchmark anglais générique.

---

# 6. Persistance, reprise et contrôle des actions

La critique de persistance est partielle : v1.2 possède déjà task, step, checkpoint, lease, event log, outbox et Canvas persistant. Ce n'est pas une recherche additionnelle à faire ; c'est un travail d'implémentation et de test.

## 6.1 Source de vérité

Room est la source de vérité. ViewModel et objets en mémoire sont des projections temporaires. WorkManager réveille/rejoue du travail différé, mais ne remplace jamais la base ni le checkpoint.

## 6.2 Reprise

- Chaque tâche a un identifiant, une version de schéma, un statut, un prochain réveil et un checkpoint.
- Chaque étape est déterministe ou idempotente autant que possible.
- Un lease empêche deux workers de reprendre la même tâche simultanément.
- Le checkpoint est écrit avant de publier l'étape suivante.
- Les appels d'outils ont une clé d'idempotence quand la destination la supporte.
- Après un crash à un moment où l'effet externe est inconnu, ne pas réexécuter automatiquement : réconcilier l'état, puis demander à l'utilisateur si l'incertitude subsiste.
- Une approbation expirée, révoquée ou dont le hash de proposition ne correspond plus n'est jamais rejouée.

## 6.3 Canvas

Le Canvas conserve son état de formulaire, paramètres, sources, hypothèses, calculs et références. Les documents originaux sont stockés séparément et chiffrés si l'utilisateur a choisi de les conserver. Une mise à jour d'application doit migrer son schéma ; si un modèle change, le checkpoint garde la version de modèle/prompt et passe par une migration ou une reprise contrôlée.

## 6.4 Human Approval

Le modèle ne possède aucun droit d'exécution. Les tool calls automatiques sont désactivés.

Une `ActionProposal` est immuable et contient : type, cible, paramètres, effets, préconditions, expiration, sources et hash canonique. Le `AuthorizationEngine` décide : `ASK_EACH_TIME`, `ALLOW_SCOPED`, `DENY`, ou confirmation renforcée. L'approbation porte sur le hash exact. Toute mutation de paramètres annule l'approbation.

Les modifications sensibles, externes, financières, destructrices ou irréversibles exigent une confirmation spécifique ; aucune permission générale ne peut les automatiser.

---

# 7. Moteurs métier : limites à respecter

| Domaine | Peut être développé maintenant | Ce qui bloque une réponse/activation publique |
|---|---|---|
| Prière | Modèle de données, calcul astronomique isolé, interface des méthodes, timezone/DST, tests numériques | Méthode de calcul, angles Fajr/Isha, Asr (standard/Hanafi), hautes latitudes, arrondis et validation par références indépendantes/mosquée. Aucun madhhab ne doit être inféré. |
| Hifz | Progression, planning, révision espacée, lecteur, alignement de passages de référence | Corpus coranique/édition approuvé ; validation des alignements ; ASR/évaluation tajwid spécialisée si promise. |
| Halal | Structure ingredient/alias, OCR import, lecteur de sources, workflow de vérification | Sources et certifications locales vérifiées, dates d'expiration et validation savante. OCR n'est pas un verdict. |
| Zakat | Modèle d'actifs, transactions locales, moteur arithmétique paramétrable | Matrice de règles et hypothèses approuvée par spécialistes par madhhab ; définition des actifs, dettes, nisab et cas modernes. |
| Héritage | Modèle d'entrée, arbre d'héritiers, moteur de calcul isolé | Juridiction civile choisie, champ de compétence, règles fiqh approuvées et validation spécialisée. Ne pas produire un calcul définitif sans pays/juridiction et scénario. |
| Voyage | Plan local, fuseaux, saisie/import d'itinéraire, calculs temporels | Règles qasr/jam' validées et données d'itinéraire saisies/importées. Pas de suivi live de vol. |
| Communauté | Annuaire et calendrier snapshots locaux | Sources, dates de fraîcheur, droits et mécanisme d'import. Pas de temps réel ni contenu utilisateur partagé. |

On peut créer les contrats et les interfaces de ces moteurs dès maintenant. Les règles non approuvées doivent être désactivées ou affichées comme non disponibles, jamais inventées par Gemma.

---

# 8. Confidentialité et publication gratuite

## 8.1 Politique d'application

- Aucun SDK d'annonce, de tracking publicitaire ou de mesure d'audience.
- Aucun SDK Billing/IAP maintenant.
- Aucun compte, backend, analytics, crash reporting distant ou synchronisation cloud.
- Aucun mécanisme de donation/sponsoring intégré dans le périmètre actuel ; toute évolution de cette politique imposera une nouvelle revue des licences NC.
- Aucun identifiant publicitaire.
- Permissions caméra, microphone, localisation et calendrier demandées uniquement au moment d'une action utilisateur et seulement si nécessaires.
- Préférer Photo Picker et SAF aux permissions globales de fichiers.
- Localisation manuelle possible ; pas de localisation en arrière-plan nécessaire au fonctionnement de base.
- Photo/audio source supprimés après extraction par défaut ; conserver uniquement si l'utilisateur le demande.
- Journal local chiffré, minimisé et consultable/supprimable.

## 8.2 Google Play : politique de confidentialité et Data safety

Même si l'application ne transmet aucune donnée personnelle, Google Play exige une fiche Data safety exacte et un lien vers une politique de confidentialité pour les applications publiées. Google indique que les données consultées uniquement sur l'appareil et non transmises hors de l'appareil ne sont pas déclarées comme « collectées » dans la Data safety section ; mais le formulaire et la politique restent requis. Les pratiques des SDK tiers doivent aussi être prises en compte. [S15]

La politique de confidentialité doit expliquer le fonctionnement exclusivement local, les types de données traitées, le stockage, la rétention, l'effacement et l'absence de transmission. Elle doit être publiée sur une URL publique accessible et être liée dans l'application et Play Console. Comme le produit n'a pas de backend, publier cette page documentaire statique sur GitHub Pages ou un hébergement statique comparable ; ce site ne reçoit aucune donnée utilisateur et ne devient pas un backend applicatif.

## 8.3 Permissions et exact alarms

Pour les rappels précis, préférer `SCHEDULE_EXACT_ALARM` avec permission accordée par l'utilisateur, si la fonction le justifie. `USE_EXACT_ALARM` est restreint par Google Play à des catégories essentielles comme alarmes et calendriers. Prévoir une notification inexacte ou un rappel dans l'application si l'accès précis est refusé. [S16][S17]

Target SDK 36 entraîne également les exigences modernes de edge-to-edge ; les insets doivent être testés sous Android 15+. [S18]

---

# 9. Ordre de développement recommandé

Ce plan n'est pas un MVP. C'est l'ordre d'exécution du produit complet, organisé afin que les dépendances risquées soient testées sans arrêter le reste.

## Lot 0 — décisions et dépôt

1. Créer le dépôt Git et le `applicationId` provisoire.
2. Ajouter ADR-001 produit gratuit/no server, ADR-002 quatre écoles/no default madhhab, ADR-003 langues, ADR-004 approbation par défaut.
3. Verrouiller Gradle Wrapper, AGP/Kotlin, Version Catalog et dependency locking.
4. Définir `minSdk 26`, `compileSdk/targetSdk 36`, ARM64 release.
5. Branch protection, revue de code, convention de commit et gestion des versions.

**Critère de sortie :** build vide reproductible sur CI et machine locale.

## Lot 1 — CI et qualité de la chaîne d'approvisionnement

1. `assembleDebug`, tests unitaires, lint, tests instrumentés.
2. Workflow manifest merged : offline build sans `INTERNET` ; aucune permission non approuvée.
3. Dependency verification, lockfiles, SBOM CycloneDX/SPDX, scans vulnérabilité/licence, secret scan.
4. Actions GitHub épinglées par SHA, permissions minimales, release seulement depuis tag protégé.
5. Aucun secret de signature dans le dépôt ; aucune donnée personnelle dans les artifacts/logs.

**Critère de sortie :** CI échoue sur permission réseau ajoutée, dépendance non vérifiée ou secret détecté.

## Lot 2 — fondations locales et sécurité

1. Modèle `UserProfile`, `TimelineItem`, `AgentTask`, `TaskStep`, `Approval`, `Canvas`, `AuditEvent`.
2. Repositories et migrations Room.
3. Spike SQLCipher + Room exact, Keystore et sauvegardes.
4. `SignatureVerifier` Ed25519, vecteurs de test et rotation des clés publiques.
5. Export/import chiffré et effacement utilisateur.

**Critère de sortie :** crash test, migrations, corruption, redémarrage et restauration réussis ; secrets et documents sensibles non présents en clair dans logs/backup.

## Lot 3 — Timeline, Canvas et autorisation

1. Timeline rendue depuis DB, sans LLM.
2. Cartes typées et états différenciés.
3. Canvas avec sauvegarde/restauration.
4. ActionProposal immuable + hash.
5. AuthorizationEngine et ToolExecutor séparés.
6. Scénarios approve/reject/expire/revoke/change-parameters/crash.

**Critère de sortie :** aucun outil modificatif ne s'exécute sans autorisation valide ; aucune reprise ne rejoue une action dont le résultat est inconnu.

## Lot 4 — Corpus manager et RAG

1. Registre de sources/licences et pipeline de validation.
2. Format `tpack`, manifeste versionné, signature, hashes et import SAF.
3. Reader documentaire avec références exactes.
4. FTS5/BM25, vector exact derrière `VectorIndexPort`, RRF.
5. EvidenceBundle et CitationValidator.
6. Jeu gold arabe/anglais, quatre écoles, cas d'abstention.
7. Benchmarks HNSW et reranker ; sélection uniquement si bénéfice démontré.

**Critère de sortie :** toutes les citations affichées se résolvent à un passage exact ; les documents non autorisés sont filtrés ; abstention correcte mesurée.

## Lot 5 — modèles et multimodalité

1. SAF ModelImporter, staging, hash, espace disque et activation atomique.
2. LiteRT-LM adapter et E2B smoke test sur S24 FE et appareils de référence.
3. ModelPreflight et profils par appareil/backend.
4. E4B seulement après mesures ; pas de seuil de RAM marketing.
5. Play AI Pack POC en parallèle ; pas de dépendance au sharding.
6. OCR anglais/arabe et ASR comme spikes séparés ; aucune décision religieuse automatique depuis l'image/audio.

**Critère de sortie :** installation, annulation, reprise, hash, chargement, stabilité, thermique et fallback documentés.

## Lot 6 — moteurs métier et validation

1. Prière et configuration explicite des méthodes.
2. Hifz, alignement au texte exact et révision espacée.
3. Voyage local et fuseaux.
4. Halal sur données documentées, scan assisté et correction humaine.
5. Zakat avec matrice de règles approuvée.
6. Héritage seulement avec juridiction et règles approuvées.
7. Communauté locale uniquement sur snapshots et source datée.

Chaque domaine sort seulement après sa propre revue documentaire, tests et validation spécialisée.

## Lot 7 — release de production

- revue sécurité OWASP MASVS ;
- revue juridique licences, confidentialité et conditions store ;
- politique de confidentialité, Data safety, permissions ;
- tests appareils physiques, langues, RTL, accessibilité ;
- validation performance/batterie/thermique ;
- tests de reprise et autorisation ;
- Play internal/closed testing ;
- rollback et migration testés ;
- notes de version, source/corpus/model manifests archivés.

---

# 10. Bloquants réels avant release — pas avant le premier commit

| ID | Porte de validation | Bloque quoi ? | Peut-on coder en parallèle ? |
|---|---|---|---|
| G-LEGAL-01 | Dossier de droits par texte, traduction, édition, embedding et pack | Distribution de chaque corpus | Oui : pipeline/manager/index |
| G-SCHOLAR-01 | Source list et validation spécialiste par domaine/madhhab | Réponses religieuses de ce domaine | Oui : contrats et moteurs génériques |
| G-DB-01 | Spike SQLCipher/Room + migrations | Stockage sensible en production | Oui : repositories/interfaces/tests |
| G-CRYPTO-01 | Ed25519 verifier, rotation/révocation | Activation des Corpus Packs signés | Oui : format/manager en test |
| G-MODEL-01 | E2B/E4B preflight et runtime réel | Annoncer la compatibilité du modèle/appareil | Oui : toute l'app sans modèle |
| G-DIST-01 | Play AI Pack internal proof, si voie Play sélectionnée | Téléchargement Play AI uniquement | Oui : SAF fallback |
| G-RAG-01 | Gold set et citation validator | Réponses religieuses générées | Oui : retrieval et reader |
| G-OCR-01 | OCR arabe évalué sur photos cibles | Scan arabe public | Oui : import manuel/UX |
| G-ASR-01 | ASR et évaluation de récitation validés | Fonction audio/Hifz correspondante | Oui : Hifz non-audio |
| G-PRAYER-01 | Méthodes/paramètres validés | Horaires présentés comme précis | Oui : UI et settings |
| G-ZAKAT-01 | Matrice de règles validée | Calcul Zakat présenté comme résultat religieux | Oui : modèle de données et UI |
| G-INHERIT-01 | Juridiction et matrice de règles validées | Calcul successoral | Oui : structures génériques |
| G-PRIV-01 | Politique, Data safety et permission review | Publication Play | Oui : développement interne |
| G-QA-01 | Tests de crash, sécurité, appareils et localisation | Release production | Oui : développement continu |

---

# 11. Points à ne plus traiter comme des questions bloquantes

Les éléments suivants sont suffisamment décidés pour coder leurs contrats et la première implémentation :

- application Android native Kotlin/Compose ;
- architecture modulaire et ports/adapters ;
- minSdk de départ API 26 ; targetSdk 36 ;
- aucun serveur produit ;
- variante offline sans INTERNET ;
- gratuite, sans annonce ni achat intégré ;
- madhhab non présélectionné ;
- quatre madhahib et arabe/anglais ;
- SAF comme import obligatoire ;
- LiteRT-LM comme provider candidat, mais pas dépendance métier ;
- Room comme persistance, SQLCipher à valider ;
- FTS5/BM25 + dense + RRF ;
- reranking conditionnel ;
- Ed25519 public-key verification ;
- approval gate indépendant du modèle ;
- stockage/checkpoint/outbox persistants ;
- données personnelles et inference locales.

Ne relancez pas une recherche générale sur « quelle architecture choisir ». Les inconnues restantes sont des essais ciblés, des décisions savantes ou des droits de source.

---

# 12. Actions à lancer maintenant — premier backlog concret

### Première tranche d'implémentation

- [ ] Créer le repository Android et les modules de base.
- [ ] Ajouter Version Catalog, Gradle Wrapper, dependency locking et verification metadata.
- [ ] Ajouter CI GitHub Actions (build, lint, tests, manifest audit, SBOM, license/security scan).
- [ ] Créer les variantes `offline` et `playDistribution` ; la seconde reste désactivée tant que Play AI Delivery n'est pas validé.
- [ ] Rendre la variante `offline` sans `INTERNET` dans le manifeste fusionné.
- [ ] Implémenter `TimelineRepository`, `AgentTaskRepository`, `ApprovalRepository`, `CanvasRepository` derrière interfaces.
- [ ] Construire le modèle Room initial et les migrations.
- [ ] Lancer le spike SQLCipher exact.
- [ ] Implémenter `ActionProposal`, `AuthorizationEngine`, `ToolExecutor` et tests de refus.
- [ ] Implémenter le ModelImporter SAF et le CorpusImporter SAF avec staging/hash/atomic activation.
- [ ] Implémenter les ports RAG et une première suite FTS5/RRF.
- [ ] Créer le registre licences/sources ; bloquer toute release de contenu non approuvé.
- [ ] Préparer les jeux d'évaluation arabes et anglais avec revue spécialisée.

### Spikes en parallèle

- [ ] SQLCipher 4.20.0 + Room 2.8.5 + SQLite 2.7.0, puis 2.7.1.
- [ ] Vérification Ed25519 sur API 26/29/33/36 et test de rotation.
- [ ] Gemma E2B LiteRT-LM sur Galaxy S24 FE ; capturer RAM/PSS, TTFT, débit, chauffe et crash.
- [ ] E4B sur au moins deux profils matériels distincts ; définir les statuts de preflight à partir de mesures.
- [ ] Play AI Packs : taille compressée/AAB, livraison, sharding si tenté, reconstruction/hash/chargement.
- [ ] Arabic OCR sur étiquettes réelles et pages de Mushaf ; aucune activation avant validation.
- [ ] Source audit : corpus hadith complet distribuable localement pour les collections retenues.

---

# 13. Réponse finale : peut-on commencer ?

**Oui. Commencer maintenant.**

Le premier commit ne doit pas être une maquette ni une démonstration. Il doit établir les fondations qui resteront dans le produit final : modules, CI, stockage, sécurité, Timeline persistante, autorisation indépendante, import, contrats documentaires et RAG testable.

Les quatre spikes mentionnés par le rapport doivent avancer en parallèle et ne bloquer que les fonctions qui dépendent d'eux. Le report de Play AI Packs n'empêche pas SAF ; le report d'E4B n'empêche pas E2B ni le fonctionnement non-IA ; le report d'un reranker n'empêche pas FTS5/dense/RRF ; le report d'un corpus savant n'empêche pas le corpus manager ou le reader.

**Ce qui est interdit :** faire passer des textes sans droits dans la release, afficher une réponse religieuse sans citation validée, utiliser le LLM comme moteur de calcul religieux, exécuter un tool call sans autorisation, ou annoncer une capacité live sans source live.

Le projet est prêt pour l'implémentation. Il ne sera prêt pour la production publique que lorsque les portes G-LEGAL, G-SCHOLAR, G-DB, G-CRYPTO, G-MODEL, G-RAG, G-PRIV et G-QA propres aux fonctions livrées seront vertes.

---

# Sources vérifiées

**S01 — Google Play target API requirements**  
https://support.google.com/googleplay/android-developer/answer/11926878?hl=en

**S02 — Play for On-device AI (beta), AI Pack sizes and delivery constraints**  
https://developer.android.com/google/play/on-device-ai

**S03 — Gemma 4 LiteRT-LM model sizes and device benchmarks**  
https://developers.google.com/edge/litert-lm/models/gemma-4

**S04 — SQLCipher Android: Room 2 and Room 3 integration**  
https://github.com/sqlcipher/sqlcipher-android

**S05 — Android Signature algorithm support**  
https://developer.android.google.cn/reference/java/security/Signature

**S06 — Google Play app free/paid pricing rules**  
https://support.google.com/googleplay/android-developer/answer/6334373

**S07 — Creative Commons FAQ: NonCommercial definition**  
https://creativecommons.org/faq/

**S08 — Tanzil Quran Text License**  
https://tanzil.net/docs/Text_License

**S09 — Tanzil Quran Translations terms**  
https://tanzil.net/trans/

**S10 — OpenITI documentation and license**  
https://openiti.org/documentation/

**S11 — Sunnah.com reproduction, copying and scraping terms**  
https://sunnah.com/about

**S12 — Sunnah.com Developers/API and offline dump status**  
https://sunnah.com/developers

**S13 — Android Storage Access Framework and persistable URI permissions**  
https://developer.android.com/training/data-storage/shared/documents-files

**S14 — ML Kit Text Recognition v2 supported languages**  
https://developers.google.com/ml-kit/vision/text-recognition/v2/languages

**S15 — Google Play User Data, Privacy Policy and Data safety requirements**  
https://support.google.com/googleplay/android-developer/answer/10144311

**S16 — Google Play exact alarm permission policy**  
https://support.google.com/googleplay/android-developer/answer/16558241

**S17 — Android exact alarm scheduling**  
https://developer.android.com/develop/background-work/services/alarms

**S18 — Android 15 edge-to-edge behavior**  
https://developer.android.com/about/versions/15/behavior-changes-15

**S19 — Tink Android setup and supported API levels**  
https://developers.google.com/tink/setup/java

**S20 — Tink supported cryptographic key types**  
https://developers.google.com/tink/supported-key-types

**S21 — Android Gradle Plugin / required Gradle versions**  
https://developer.android.com/build/releases/about-agp

**S22 — Kotlin Gradle Plugin / AGP compatibility matrix**  
https://kotlinlang.org/docs/gradle-configure-project.html
