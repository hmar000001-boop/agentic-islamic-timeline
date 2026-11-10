---
title: "Cahier d'architecture de production — Timeline agentique"
subtitle: "Application Android sunnite, locale, vérifiable et contrôlée par l'utilisateur"
author: "Spécification produit et architecture"
date: "5 novembre 2026"
lang: fr-FR
---

# Sommaire

- **Document de référence**
- **0.** Décisions produit et limites structurantes
- **0.4.** Méta-revue du cahier v1 et arbitrages
- **1.** Vision du produit et exigences
- **2.** Architecture Android et organisation du dépôt
- **3.** Contrats entre composants
- **4.** Modèle de données local
- **5.** Corpus sunnite et gouvernance savante
- **6.** Ingestion, chunking et formats de corpus
- **7.** RAG hybride et validation des preuves
- **8.** Génération locale et gestion des modèles
- **9.** Orchestration agentique
- **10.** Human Approval & Action Authorization Engine
- **11.** Moteurs de domaine
- **12.** Temps, notifications et cycle de vie Android
- **13.** Sécurité et confidentialité
- **14.** Langues, RTL et accessibilité
- **15.** Distribution locale des corpus et modèles
- **16.** Dépendances et toolchain candidates
- **17.** GitHub Actions et chaîne d'approvisionnement
- **18.** Stratégie de tests et critères d'acceptation production
- **19.** Critères de publication
- **20.** Risques et décisions techniques ouvertes
- **21.** ADR — décisions techniques formalisées
- **22.** Plan d'ingénierie de production
- **23.** Références techniques consultées

\newpage

# Document de référence

| Champ | Valeur |
|---|---|
| Produit | Timeline agentique |
| Version du document | 1.2 — version finale après méta-revue et vérification de cohérence |
| Date de référence | 5 novembre 2026 |
| Plateforme | Android natif uniquement |
| Périmètre religieux | Sunnite, quatre madhahib |
| Langues de lancement | Arabe et anglais |
| Serveur applicatif | Aucun |
| Exécution IA | Sur l'appareil, modèles installés localement |
| CI/CD | GitHub Actions |
| Statut | Base de conception de production, non preuve d'implémentation |

## Conventions

- **DÉCIDÉ** : contrainte confirmée par le propriétaire du produit.
- **RECOMMANDÉ** : décision architecturale proposée dans ce cahier.
- **À BENCHMARKER** : choix qui ne peut pas être figé sans mesure sur les appareils cibles.
- **BLOQUANT** : condition obligatoire avant activation publique d'une fonctionnalité.
- **ADR** : Architecture Decision Record, décision motivée et traçable.

Ce document décrit une cible de production et des décisions de conception, pas un produit déjà implémenté. Les modèles, runtimes, mécanismes de distribution, moteurs vectoriels, licences documentaires et profils matériels doivent être validés par des preuves reproductibles avant publication. Toute mention « à benchmarker » ou « bloquant » correspond à une condition de sortie, non à une promesse de compatibilité.

# 0. Décisions produit et limites structurantes

## 0.1 Décisions approuvées

| ID | Décision | Conséquence |
|---|---|---|
| DEC-001 | Android uniquement | Kotlin et APIs Android natives ; aucun client iOS/web |
| DEC-002 | Sunnite uniquement | Corpus, gouvernance et règles couvrent le sunnisme |
| DEC-003 | Quatre écoles | Hanafi, Maliki, Shafi'i et Hanbali ; aucune école n'est implicitement privilégiée |
| DEC-004 | Arabe et anglais au lancement | UI, recherche et contenus affichés prévus pour ces deux langues |
| DEC-005 | Extensible aux langues futures | Le modèle de données ne doit pas coder en dur deux langues |
| DEC-006 | Aucun serveur applicatif | Pas de backend propriétaire, d'inférence distante, de compte obligatoire ou de synchronisation cloud |
| DEC-007 | Local-first | Données privées, RAG, calculs et historique sont locaux |
| DEC-008 | Corpus hybride | Noyau de référence validé + collections complémentaires clairement identifiées |
| DEC-009 | Approbation humaine | Toute action modificatrice exige une approbation par défaut |
| DEC-010 | Autorisations permanentes limitées | Autorisées uniquement pour une action précise, des paramètres et des limites déterminés par l'utilisateur |
| DEC-011 | Actions sensibles | Confirmation individuelle obligatoire ; aucune permission générale ne peut les automatiser |
| DEC-012 | GitHub Actions | CI, tests, SBOM et production d'artefacts seulement ; pas d'exécution de logique utilisateur |
| DEC-013 | Aucun madhhab présélectionné | Le profil initial reste non configuré ; l'utilisateur choisit une école, « sans préférence » ou « comparer les quatre » |

## 0.2 Interprétation normative de « aucun serveur »

Le produit ne possède **aucun backend applicatif** : aucun compte propriétaire obligatoire, API de l'application, serveur d'inférence, service RAG distant, base cloud, télémétrie distante, synchronisation distante ou transmission de données privées. Les prompts, questions religieuses, documents, audio, localisation, préférences et historiques restent sur l'appareil.

Il faut distinguer l'absence de serveur produit de deux services tiers de distribution facultatifs : Google Play peut livrer l'application et, si la voie bêta est validée, ses AI Packs ; un navigateur ou un ordinateur peut télécharger des fichiers publics que l'utilisateur importe ensuite. Ces services ne reçoivent pas les données personnelles de l'application. Ils ne constituent toutefois pas une exécution « sans connexion » : l'installation ou le téléchargement demande une connexion Internet externe.

Deux variantes de distribution sont prévues :

- **`playDistribution`** : variante Google Play. Si Play for On-device AI est retenu, la dépendance officielle Play AI Delivery est la seule raison autorisée d'ajouter les permissions réseau nécessaires. Aucun client HTTP applicatif, SDK analytics, publicité, compte ou upload ne doit être ajouté. Les permissions du manifeste fusionné doivent être auditées à chaque release. Le réseau sert exclusivement à la livraison des packs par Google Play et au téléchargement de l'application.
- **`offlineSideload`** : variante sans distribution Play. Aucun droit `INTERNET` dans le manifeste final. Les modèles et Corpus Packs sont importés via SAF depuis un stockage local, un ordinateur ou un support externe. Les fichiers peuvent avoir été téléchargés par l'utilisateur au moyen d'un navigateur, hors de l'application.

La variante Play doit également rester entièrement utilisable hors ligne après installation des ressources. Si la dépendance Play AI Delivery ne peut être isolée ou auditée, la variante de distribution sans permission réseau reste disponible et utilise l'import SAF.

Conséquences fonctionnelles visibles dans l'interface :

- Pas de suivi automatique des retards ou du statut live des vols. Un trajet est un horaire saisi/importé ; toute mise à jour dépend d'une nouvelle saisie ou d'un fichier importé.
- Pas d'événements communautaires live ; annuaire et agenda sont des snapshots locaux qui affichent leur source et leur date d'actualisation.
- Pas d'envoi intégré à un savant ; l'application prépare un dossier local exportable. Le partage vers une autre application exige une prévisualisation et une confirmation distincte.
- Pas de synchronisation entre appareils ni de sauvegarde cloud gérée par le produit.
- Pas de mise à jour automatique des corpus via un serveur applicatif. Les corpus sont inclus dans une release, livrés par une distribution statique tierce explicitement choisie ou importés via SAF.
- Aucun prix, nisab fondé sur un cours, statut de certification ou événement n'est affiché comme « actuel » sans source horodatée présente sur l'appareil.

L'interface doit afficher le statut de fraîcheur (`STATIC_PACK`, `USER_IMPORTED`, `USER_ENTERED`, `STALE`, `UNKNOWN`) et la date/source lorsque disponibles. Le terme « live » est réservé à une donnée réellement actualisée par un fournisseur en temps réel ; cette architecture n'en propose pas.

## 0.3 Limites de ce cahier

Ce cahier définit l'architecture logicielle, les contrats, les schémas, les contrôles et les critères techniques. Il ne constitue pas :

- une fatwa ni une sélection finale d'autorités religieuses ;
- un avis juridique sur les licences des ouvrages ;
- une garantie de compatibilité de tous les modèles sur tous les téléphones ;
- une validation de qualité d'un corpus qui n'a pas encore été audité ;
- une autorisation de distribuer un dataset dont les droits des sources sous-jacentes sont inconnus.

## 0.4 Méta-revue du cahier v1 — statut des critiques et arbitrages

La revue externe a correctement identifié plusieurs insuffisances de précision. Elle a cependant décrit comme absents certains éléments qui existaient déjà dans v1 sous forme initiale : le chunking par type documentaire (6.2), la récupération hybride et les citations (7), les états d'AgentTask et de Canvas (4 et 9), ainsi que l'import SAF (15). Le statut « partiel » signifie donc que la fondation était présente mais que les contrats, critères opérationnels ou mécanismes de livraison n'étaient pas assez précis.

| Point de revue | Statut | Arbitrage v1.2 |
|---|---|---|
| Alignement Timeline / Agent / Cards / Canvas et principes P1–P10 | CORRECT | Conservé sans changement de principe. |
| LiteRT-LM présenté comme le runtime Android de production définitif | PARTIEL | LiteRT-LM Kotlin est un candidat pris en charge pour E2B/E4B, mais il ne devient pas une dépendance métier. Le fournisseur runtime reste abstrait ; Android AI Core/Gemini Nano est une piste distincte à tester sur appareils compatibles, sans supposer qu'il fournit le même modèle/version contrôlable. |
| AGP 9.3.1 / Kotlin 2.4.21 | CORRECT, sous réserve d'intégration | Intersection de compatibilité documentée retenue pour le snapshot ; résolution Gradle, KSP, Compose, LiteRT-LM et plugins natifs restent à prouver dans le build réel. |
| RAG hybride BM25 + dense + RRF + reranking | CORRECT au niveau conceptuel | Détails de chunking, métadonnées, interfaces, stratégie adaptative, validation des citations et jeu d'évaluation ajoutés en 6, 7 et 18. |
| « sqlite-vec implémente HNSW + BM25 + RRF » | NOT CORRECT | sqlite-vec est un index/retrieval vectoriel distinct ; FTS5/BM25 et RRF sont des couches séparées. Aucun HNSW n'est attribué à sqlite-vec dans cette architecture. |
| Mécanisme de distribution modèles/corpus insuffisamment tranché | PARTIEL | SAF était déjà prévu ; v1.2 ajoute Play AI Packs bêta conditionnels, limites, premier parcours, variantes réseau et fallback. Le sharding d'un modèle en plusieurs AI Packs reste non prouvé et bloquant. |
| E4B nécessite environ 2 Go et un appareil d'au moins 7 Go de RAM | PARTIEL pour le risque ; NOT CORRECT pour le seuil universel | Le coût mémoire est réel, mais dépend du runtime, backend, variante, contexte et appareil. Les benchmarks S26 Ultra publiés montrent 3 283 Mo de mémoire CPU de processus en backend CPU et 710 Mo dans la colonne correspondante en backend GPU pour E4B ; ce ne sont pas un seuil de RAM totale ni une garantie pour un autre téléphone. Aucun seuil fixe de 7 Go n'est adopté. |
| RAG pas assez spécifié | PARTIEL | Chunking était déjà amorcé ; v1.2 rend normatifs les unités par type, le parentage, métadonnées, ports, RRF, reranking adaptatif et validation des claims. |
| Reranking mobile potentiellement coûteux et non conditionnel | CORRECT | Le reranking est facultatif, conditionnel et désactivable ; le RRF seul reste un mode dégradé, avec abstention si les preuves deviennent insuffisantes. |
| Persistance et reprise agentiques insuffisamment opérationnelles | PARTIEL | Tables task/step/canvas existaient ; v1.2 ajoute checkpoints versionnés, leases, événements, outbox, algorithme de reprise et règles anti-rejeu. |
| Aucun serveur incompatible avec les cartes de vol/communauté | NOT CORRECT comme contradiction d'architecture ; PARTIEL comme exigence UX | v1 indiquait déjà l'absence de données live. v1.2 impose des labels de fraîcheur, un âge de données et l'interdiction d'afficher des propositions impliquant une actualisation non disponible. |
| Approche hybride Play / SAF proposée | PARTIEL | Acceptée comme stratégie conditionnelle, mais le découpage E2B/E4B en fragments d'AI Packs n'est pas une fonction officiellement documentée. Il faut démontrer chargement et reconstruction avant de l'annoncer. |

Les dimensions de performance publiées par Google pour Gemma 4 proviennent d'un scénario de benchmark précis sur Galaxy S26 Ultra ; elles ne doivent pas être converties en promesses de performance pour le Galaxy S24 FE, ni en seuils matériels universels. [R03][R33][R34]

# 1. Vision du produit et exigences

## 1.1 Surfaces utilisateur

1. **Timeline** : flux temporel principal, construit à partir d'événements confirmés, d'habitudes, de tâches et de propositions.
2. **Decision Cards** : propositions explicables, avec preuves, paramètres, conséquences et commandes d'approbation.
3. **Canvas** : espace de travail persistant pour les tâches complexes (Hifz, voyage, Zakat, héritage, analyse de produit).
4. **Knowledge Reader** : lecteur de sources avec passage exact, contexte parent, édition, traduction et surlignage.
5. **Agent Controls** : permissions, historique d'actions, modèles installés, corpus et paramètres de proactivité.

## 1.2 Types d'éléments de timeline

| Type | Autorité | Exemple | Peut être modifié par l'agent ? |
|---|---|---|---|
| Événement confirmé | Source utilisateur ou moteur déterministe | Dhuhr calculé, rendez-vous importé | Non sans proposition et autorisation |
| Habitude / objectif | Configuration utilisateur | Révision Hifz | L'agent peut proposer une adaptation |
| Proposition | Agent | Session Hifz de 12 minutes | Oui seulement après le contrôle d'autorisation |
| Observation | Source locale | Session terminée | Non ; correction par l'utilisateur |
| Élément historique | Journal local | Session accomplie | Non, sauf correction explicitement auditée |

La timeline ne dépend jamais d'un LLM pour s'afficher. Elle doit se reconstruire depuis l'état persistant local si aucun modèle n'est installé.

## 1.3 Exigences fonctionnelles

### Timeline et contexte

- FR-TL-01 : construire une vue du jour et des prochains jours à partir des éléments locaux.
- FR-TL-02 : préserver la distinction entre événement confirmé, observation, habitude et proposition.
- FR-TL-03 : permettre de consulter les éléments passés ; ne pas supprimer silencieusement l'historique.
- FR-TL-04 : recalculer les éléments temporels lors d'un changement de fuseau, d'heure système ou de paramètres de prière.
- FR-TL-05 : expliquer la raison de chaque proposition proactive.
- FR-TL-06 : limiter et configurer les notifications et les heures silencieuses.

### Agent

- FR-AG-01 : recevoir des événements explicites : action utilisateur, échéance, changement de contexte autorisé ou résultat d'un moteur de domaine.
- FR-AG-02 : produire des propositions structurées et non des instructions exécutables libres.
- FR-AG-03 : utiliser le RAG lorsque la réponse dépend d'une source religieuse.
- FR-AG-04 : demander une précision lorsque les faits nécessaires ne sont pas disponibles.
- FR-AG-05 : s'abstenir lorsque les preuves sont insuffisantes ou contradictoires.
- FR-AG-06 : persister les tâches en cours et les propositions en attente.

### Sources et RAG

- FR-RAG-01 : chaque affirmation religieuse visible doit référencer un ou plusieurs passages de source connus du dépôt documentaire.
- FR-RAG-02 : ouvrir une source au passage exact et montrer son contexte parent.
- FR-RAG-03 : afficher auteur, ouvrage, édition, langue, localisation et classification lorsque pertinents.
- FR-RAG-04 : distinguer source primaire, commentaire, avis juridique, traduction et contenu complémentaire.
- FR-RAG-05 : montrer les divergences pertinentes entre les quatre écoles.
- FR-RAG-06 : ne jamais présenter un score de similarité comme une probabilité de vérité religieuse.

### Approbation

- FR-AUTH-01 : toute action qui modifie un état est refusée par défaut tant qu'elle n'est pas autorisée.
- FR-AUTH-02 : une autorisation permanente est bornée par type d'action, cible, paramètres, plage, durée et conditions.
- FR-AUTH-03 : les actions sensibles exigent toujours une confirmation spécifique.
- FR-AUTH-04 : toute modification des paramètres d'une proposition annule l'approbation antérieure.
- FR-AUTH-05 : refus, expiration et révocation empêchent l'exécution.
- FR-AUTH-06 : chaque exécution dispose d'un reçu vérifiable et, si possible, d'une annulation.

## 1.4 Exigences non fonctionnelles

| ID | Exigence | Critère de validation |
|---|---|---|
| NFR-OFF-01 | Fonctionnement hors ligne | Les tests fonctionnels essentiels réussissent en mode avion |
| NFR-PRIV-01 | Aucune exfiltration | Variante offline : aucun droit réseau ; variante Play : seules permissions réseau indispensables à Play AI Delivery, aucun client réseau produit ni upload de données |
| NFR-SEC-01 | Confidentialité des données | Chiffrement des données privées et des pièces jointes sensibles ; clés sous Android Keystore |
| NFR-SEC-02 | Autorisation indépendante du LLM | Aucun tool call modificatif n'atteint un exécuteur sans décision positive |
| NFR-DATA-01 | Provenance documentaire | 100 % des citations affichées résolvent vers un passage exact et une version de corpus |
| NFR-DATA-02 | Intégrité corpus | Refus d'un pack si signature, hash, licence ou schéma échoue |
| NFR-REC-01 | Reprise | État des tâches et demandes d'approbation restauré après arrêt du processus |
| NFR-I18N-01 | RTL | Tests de référence pour l'arabe, les nombres et les textes bidirectionnels |
| NFR-MODEL-01 | Mode dégradé | L'application reste utilisable si le modèle est absent, fermé ou en échec |
| NFR-PERF-01 | Réactivité | Budget de performance défini par benchmarks sur appareils de référence avant release |

# 2. Architecture Android et organisation du dépôt

## 2.1 Architecture de référence

Architecture en couches, état piloté par les données, flux unidirectionnel et single-activity. Les composants Android sont des points d'entrée éphémères : ils ne détiennent pas l'état métier. Les repositories locaux sont les sources de vérité.

### Modules Gradle recommandés

```text
:app
:core:common
:core:designsystem
:core:localization
:core:security
:core:persistence
:core:time
:core:timeline-api
:core:agent-api
:core:agent-runtime
:core:authorization
:core:tools-api
:core:tools-android
:core:knowledge-api
:core:corpus
:core:retrieval
:core:model-runtime
:core:scheduler
:feature:timeline
:feature:canvas
:feature:knowledge-reader
:feature:prayer
:feature:hifz
:feature:travel
:feature:halal
:feature:zakat
:feature:inheritance
:feature:community-local
:feature:settings
```

Le nombre de modules est un objectif de frontières, pas une exigence de créer un module vide pour chaque package. Aucun cycle Gradle n'est autorisé.

## 2.2 Règles de dépendances

- `:feature:*` peut dépendre des contrats `:core:*` et de son propre domaine.
- `:core:agent-runtime` dépend de `agent-api`, `knowledge-api`, `tools-api` et `authorization` uniquement via interfaces.
- `:core:retrieval` dépend de `knowledge-api`, du corpus et des ports d'embedding/vectorisation.
- `:core:tools-android` est le seul endroit qui dépend directement des APIs Android de calendrier, notifications, alarmes et partage.
- `:core:authorization` ne dépend ni du LLM ni des fonctionnalités UI.
- `:core:security` ne dépend pas de Compose.
- Les moteurs de domaine n'importent jamais LiteRT-LM, Gemma ou les classes Compose.

## 2.3 Responsabilités

| Module | Responsabilité et propriétaire de l'état |
|---|---|
| `:app` | Composition racine, navigation, DI, initialisation |
| `:core:common` | Types primitifs, horloge injectable, résultat et erreurs |
| `:core:designsystem` | Composants visuels, thèmes, accessibilité |
| `:core:localization` | Locale active, formats, RTL et règles bidi |
| `:core:security` | Keystore, chiffrement, empreintes, vérification Ed25519 |
| `:core:persistence` | Room, migrations, transactions, repositories techniques |
| `:core:time` | Fuseaux, calendrier, horodatages, planification |
| `:core:timeline-api` | Contrats de cartes et événements exposés à l'interface |
| `:core:agent-api` | Événements, tâches, propositions, résultats structurés |
| `:core:agent-runtime` | Orchestration, planification, boucle d'agent et reprise |
| `:core:authorization` | Politique, grants, approbations, révocation et execution gate |
| `:core:tools-api` | Descripteurs de capacités et contrats de tool calls |
| `:core:tools-android` | Adaptateurs Android contrôlés |
| `:core:knowledge-api` | Contrats documentaires et de retrieval |
| `:core:corpus` | Packs, manifeste, provenance, versions et importation |
| `:core:retrieval` | Normalisation, FTS, dense, RRF, re-ranking, preuves |
| `:core:model-runtime` | Model manager, LiteRT-LM, embeddings, OCR/ASR abstraits |
| `:core:scheduler` | WorkManager, AlarmManager et réconciliation des échéances |
| `:feature:timeline` | Vue principale et interactions de cartes |
| `:feature:canvas` | Canevas persistants et étapes complexes |
| `:feature:knowledge-reader` | Lecteur de sources et preuves |
| `:feature:prayer` | Paramètres et calculs de prière |
| `:feature:hifz` | Plan de mémorisation et révision |
| `:feature:travel` | Fuseaux, itinéraires importés et règles de voyage référencées |
| `:feature:halal` | Ingrédients, étiquettes et certification documentée |
| `:feature:zakat` | Saisie, calcul explicable et dossier de revue |
| `:feature:inheritance` | Modèle de succession et calcul conditionné à revue experte |
| `:feature:community-local` | Répertoires et événements importés localement |
| `:feature:settings` | Consentements, permissions, langues et stockage |

# 3. Contrats entre composants

Les contrats ci-dessous sont des formes conceptuelles Kotlin. Les sérialiseurs utilisés en persistance doivent être versionnés. Les données produites par un LLM sont considérées comme non fiables jusqu'à validation.

## 3.1 Contexte utilisateur

```kotlin
data class ContextSnapshot(
    val snapshotId: String,
    val capturedAt: Instant,
    val zoneId: String,
    val localeTag: String,
    val location: LocationContext?,
    val calendarItems: List<CalendarFact>,
    val domainFacts: List<DomainFact>,
    val userPreferencesVersion: Long
)

data class LocationContext(
    val latitude: Double?,
    val longitude: Double?,
    val source: LocationSource, // MANUAL, ONE_SHOT_PERMISSION, IMPORTED
    val observedAt: Instant?,
    val precision: LocationPrecision
)
```

Une absence de localisation est une valeur valide. La fatigue, la maladie, l'exemption ou tout autre état sensible ne sont jamais inférés à partir d'usage, d'horaires ou de capteurs. Ils ne peuvent venir que d'une saisie volontaire et révocable.

## 3.2 Sources et preuves

```kotlin
data class EvidenceRef(
    val evidenceId: String,
    val corpusPackId: String,
    val corpusVersion: String,
    val sourceId: String,
    val documentId: String,
    val passageId: String,
    val editionId: String?,
    val languageTag: String,
    val locator: SourceLocator,
    val exactTextHash: String,
    val relationToClaim: EvidenceRelation
)

enum class EvidenceRelation { SUPPORTS, QUALIFIES, CONTRADICTS, CONTEXT_ONLY }

data class Claim(
    val claimId: String,
    val text: String,
    val claimType: ClaimType,
    val evidenceIds: List<String>,
    val qualification: String?
)
```

Un `EvidenceRef` n'est jamais fourni librement par Gemma. Le modèle ne peut choisir que des identifiants reçus dans son contexte ; l'application vérifie ensuite leur existence dans la version de corpus réellement interrogée.

## 3.3 Réponse structurée

```kotlin
data class AnswerEnvelope(
    val answerId: String,
    val taskId: String,
    val languageTag: String,
    val requestedMadhhab: SunniMadhhab?,
    val claims: List<Claim>,
    val evidence: List<EvidenceRef>,
    val evidenceStatus: EvidenceStatus,
    val unresolvedQuestions: List<String>,
    val limitations: List<String>,
    val reviewerStatus: ReviewerStatus
)

enum class EvidenceStatus {
    SUPPORTED, PARTIALLY_SUPPORTED, DISAGREEMENT,
    INSUFFICIENT, OUT_OF_SCOPE, NEEDS_REVIEW
}
```

Les statuts sont calculés par le pipeline de validation, pas par une auto-évaluation du LLM. Aucun pourcentage de « confiance religieuse » n'est affiché.

## 3.4 Proposition d'action

```kotlin
data class ActionProposal(
    val proposalId: String,
    val taskId: String,
    val actionType: String,
    val targetId: String?,
    val canonicalParameters: ByteArray,
    val parametersSha256: String,
    val reason: String,
    val consequenceSummary: String,
    val sourceEvidenceIds: List<String>,
    val expectedResourceVersion: Long?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val reversibility: Reversibility,
    val impactClass: ImpactClass
)
```

Le `parametersSha256` est calculé par l'application à partir d'une sérialisation canonique déterministe. Une approbation est liée à ce hash, au type d'action et à la cible. Toute modification crée une nouvelle proposition et rend l'approbation précédente inutilisable.

## 3.5 Ports principaux

```kotlin
interface RetrievalPort {
    suspend fun retrieve(request: RetrievalRequest): EvidenceBundle
}

interface LexicalSearchPort {
    suspend fun search(request: LexicalSearchRequest): List<RankedPassage>
}

interface EmbeddingProvider {
    val modelDescriptor: EmbeddingModelDescriptor
    suspend fun embedQuery(text: String, languageTag: String): QueryVector
    suspend fun embedPassages(texts: List<String>, languageTag: String): List<PassageVector>
}

interface VectorIndexPort {
    suspend fun search(request: VectorSearchRequest): List<RankedPassage>
}

interface RerankerPort {
    val modelDescriptor: RerankerDescriptor
    suspend fun rerank(query: String, candidates: List<RankedPassage>): List<RankedPassage>
}

interface EvidenceResolverPort {
    suspend fun resolve(ids: Set<String>, corpusSnapshot: CorpusSnapshot): List<ResolvedEvidence>
}

interface CitationValidatorPort {
    suspend fun validate(output: StructuredModelOutput, evidence: EvidenceBundle): CitationValidationResult
}

interface ModelPort {
    suspend fun generate(request: GenerationRequest): StructuredModelOutput
}

interface AuthorizationPort {
    suspend fun evaluate(proposal: ActionProposal): AuthorizationDecision
    suspend fun approve(proposalId: String, userConfirmation: UserConfirmation): ApprovalReceipt
    suspend fun revoke(grantId: String): Unit
}

interface ToolExecutorPort {
    suspend fun execute(approved: ApprovedAction): ActionExecutionReceipt
    suspend fun undo(receiptId: String): UndoResult
}
```

Les implémentations concrètes restent internes. L'agent ne reçoit jamais une référence directe à un exécuteur Android.

# 4. Modèle de données local

## 4.1 Séparation des magasins

**Base utilisateur chiffrée** : données personnelles, événements, tâches, permissions, actions, historique, documents et préférences sensibles.

**Base documentaire en lecture seule** : textes, éditions, traductions, références, classifications et index lexicaux du corpus.

**Index vectoriel** : artefact versionné lié à un modèle d'embedding précis. Il peut être reconstruit depuis le corpus ; il n'est pas la source de vérité documentaire.

**Répertoire des modèles** : poids de modèles importés, manifestes, hashes et état d'installation. Les poids ne sont pas sauvegardés automatiquement.

## 4.2 Tables utilisateur principales

| Table | Champs principaux | Règles |
|---|---|---|
| `timeline_item` | id, type, titleKey, startsAt, endsAt, zoneId, origin, status, sourceVersion | État possédé par TimelineRepository |
| `context_event` | id, eventType, occurredAt, payloadEncrypted, provenance | Événement append-only ; payload minimal |
| `agent_task` | id, goalType, status, createdAt, updatedAt, stateVersion, nextEligibleAt | État métier autoritatif ; pas de mémoire uniquement en RAM |
| `agent_task_step` | id, taskId, stepType, status, inputHash, outputRef, attempt, idempotencyKey | Étape typée et réexécutable ou réconciliable |
| `agent_task_checkpoint` | id, taskId, checkpointVersion, currentStepId, stateSchemaVersion, payloadEncrypted, payloadHash, createdAt | Reprise depuis un état cohérent et versionné |
| `agent_task_lease` | taskId, leaseOwner, acquiredAt, heartbeatAt, expiresAt, fencingToken | Évite deux workers concurrents ; lease récupérable après crash |
| `agent_task_event` | eventId, taskId, sequence, eventType, payloadEncrypted, occurredAt | Journal ordonné des transitions métier |
| `canvas_session` | id, domain, status, stateEncrypted, currentStep, version, stateSchemaVersion, updatedAt | Autosave et restauration sans modèle |
| `canvas_step` | id, canvasId, stepKey, status, inputRef, outputRef, evidenceIds, stepVersion | Étapes et preuves d'un canevas |
| `canvas_snapshot` | id, canvasId, snapshotVersion, payloadEncrypted, payloadHash, createdAt | Snapshots cohérents pour restauration et rollback |
| `canvas_artifact` | id, canvasId, artifactType, encryptedPath, sha256, retentionPolicy | Pièces jointes liées au canevas |
| `action_outbox` | id, proposalId, actionHash, idempotencyKey, state, attempt, externalRef, updatedAt | Réconciliation des effets de bord Android ; aucun rejeu aveugle |
| `user_preference` | key, valueEncrypted, version, updatedAt | Source unique de préférences |
| `permission_grant` | id, actionType, scope, constraints, expiresAt, revokedAt, policyVersion | Grant limité et révocable |
| `pending_approval` | id, proposalId, proposalHash, state, expiresAt, decisionAt | Une approbation par proposition exacte |
| `action_execution` | id, proposalHash, idempotencyKey, state, receipt, undoState | Empêche doublons et permet audit |
| `audit_event` | id, sequence, eventType, timestamp, payloadHash, previousMac, recordMac | Journal local tamper-evident |
| `hifz_plan` | id, mushafEditionId, planVersion, preferences | Référence à une édition vérifiée |
| `hifz_review` | id, planId, passageRef, reviewedAt, outcome | Progression locale |
| `prayer_profile` | id, calculationMethod, asrRule, highLatitudeRule, zoneId | Paramètres explicitement choisis |
| `document_attachment` | id, encryptedPath, mime, size, sha256, retentionPolicy | Pièces jointes chiffrées |
| `installed_corpus_pack` | packId, version, manifestHash, installedAt, status | Packs actifs et précédents |
| `installed_model` | modelId, version, fileHash, runtimeFormat, status | Poids locaux importés |

## 4.3 Tables documentaires principales

- `source`: organisation/personne/ouvrage, URI bibliographique, type, licence et statut de validation.
- `document`: œuvre intellectuelle, auteur attribué, langue originale et relations bibliographiques.
- `edition`: éditeur, éditeur scientifique, année, numérotation, ISBN/identifiant et empreinte.
- `passage`: texte original immuable, localisation et parent documentaire.
- `translation`: passage source, texte traduit, auteur/traducteur, édition et langue.
- `hadith_variant`: chaîne de transmission, matn, collection et relation aux variantes.
- `hadith_grade`: classification, classificateur, ouvrage de classification, édition et citation de justification.
- `fiqh_opinion`: école, auteur, ouvrage, question, statut attribué, conditions, exceptions et références.
- `review_record`: réviseur, domaine de compétence, décision, version et date.
- `license_record`: identifiant de licence, attribution requise, restrictions, preuve d'autorisation.
- `corpus_release`: version, manifeste, signature, dépendances et date de publication.

Les données du corpus sont traitées comme des données non fiables du point de vue de l'exécution. Aucun document ne peut injecter du code, un outil ou une instruction de système.

## 4.4 Checkpoints, leases, outbox et Canvas durable

Room est la source de vérité. WorkManager est un mécanisme de réveil/exécution différable, pas la base de l'état agentique et pas un moteur d'orchestration dont l'état interne remplacerait `agent_task`.

Un checkpoint contient au minimum :

- `taskId`, `checkpointVersion`, `stateSchemaVersion` et `currentStepId` ;
- l'objectif typé, la version du plan et les références des entrées ;
- les IDs et hashes des preuves utilisées ainsi que les versions de Corpus Packs ;
- `modelId`, révision, paramètres de génération pertinents et version du runtime ;
- la proposition en attente (ID + hash) si elle existe ;
- les résultats de moteurs de domaine et leurs versions de règles ;
- les références vers des pièces jointes chiffrées, jamais les octets bruts dans WorkManager.

Le checkpoint ne conserve pas les chaînes de raisonnement privées du modèle. Il conserve les entrées, décisions métier, preuves, sorties structurées et transitions nécessaires à l'audit et à la reprise.

**Protocole d'exécution d'une tâche :**

1. Le Use Case persiste `RUNNABLE` et le checkpoint initial dans une transaction Room.
2. Le scheduler programme un `OneTimeWorkRequest` unique, dont l'input est limité à `taskId` et `stepId` ; les données privées sont relues dans Room.
3. Le Worker acquiert un lease atomique avec un `fencingToken`, vérifie `stateVersion`, l'annulation et les conditions de reprise.
4. Il exécute une étape bornée et sans effet de bord, ou crée une `ActionProposal` persistée.
5. Il écrit la sortie et le checkpoint avant de libérer le lease ou de planifier l'étape suivante.
6. Si l'utilisateur doit répondre, l'état devient `WAITING_FOR_INPUT` ou `WAITING_FOR_APPROVAL` ; aucun Worker ne reste vivant en attente.
7. À la réouverture de l'app ou après un redémarrage, le réconciliateur recherche les leases expirés et les tâches `RUNNING` sans lease actif, puis les marque `INTERRUPTED`/`RESUMABLE` après validation du dernier checkpoint.

**Reprise des approbations :** une demande `WAITING_FOR_APPROVAL` est restaurée comme carte en attente. Elle n'est jamais exécutée lors du boot ou de la reprise. Juste avant exécution, l'application revalide l'expiration, le hash de proposition, la version de politique, le grant éventuel, la version de ressource et les préconditions. Si une seule valeur a changé, elle crée une nouvelle proposition et demande une nouvelle autorisation.

**Effets de bord et outbox :** une écriture dans Calendar Provider ou un autre outil Android ne peut pas toujours être atomique avec la transaction Room. L'état d'outbox évolue `PREPARED -> EXECUTING -> APPLIED_UNVERIFIED -> VERIFIED`, avec chemins alternatifs `FAILED` ou `OUTCOME_UNKNOWN`. En cas d'interruption après l'appel externe mais avant l'enregistrement local, le système recherche l'objet externe par identifiant, marqueur et empreinte ; il ne rejoue jamais automatiquement une opération dont le résultat est ambigu. Le produit ne promet pas une garantie exactly-once impossible à démontrer pour tous les providers.

**Canvas :** les paramètres acceptés, étapes, preuves, hypothèses, résultats et pièces jointes sont enregistrés dans Room. Un changement accepté par l'utilisateur crée un nouveau `canvas_step`/checkpoint ; les éditions en cours peuvent être sauvegardées avec debounce, mais les transitions d'étape et les confirmations sont transactionnelles. Un canevas s'ouvre même si aucun modèle n'est installé. Si son schéma ou son moteur a changé, une migration versionnée ou un snapshot en lecture seule est proposé ; les anciens résultats gardent leurs versions de modèle, règles et corpus.


# 5. Corpus sunnite et gouvernance savante

## 5.1 Périmètre documentaire

Le périmètre visé comprend :

1. Coran : texte arabe de référence, métadonnées de sourates/versets, éditions et traductions sous licence.
2. Hadith : Sahih al-Bukhari, Sahih Muslim, Sunan Abi Dawud, Jami' al-Tirmidhi, Sunan al-Nasa'i, Sunan Ibn Majah et Muwatta Malik, plus collections supplémentaires après validation.
3. Commentaires : tafsir, commentaires de hadith et ouvrages d'explication.
4. Fiqh : sources des quatre écoles sunnites et avis attribués précisément.
5. Sources complémentaires : cours, articles et explications, marqués comme complémentaires et non interchangeables avec les sources primaires.

**Règle doctrinale :** les « six livres » ne sont pas six recueils uniformément sahih. Les classifications de hadith doivent être attachées à un classificateur et à une référence précise. Une collection, un numéro ou une catégorie ne suffit pas à inférer automatiquement l'authenticité.

## 5.2 Représentation des quatre écoles

Valeurs de référence : `HANAFI`, `MALIKI`, `SHAFII`, `HANBALI`.

Un avis doit être attribué à son auteur et à son ouvrage ; son école ne doit pas être déduite seulement d'une catégorie de recherche. Les divergences internes à une école, les avis minoritaires et les conditions propres à une opinion doivent pouvoir être représentés. « Avis de l'école » ne doit pas être un champ booléen simplificateur. Aucun madhhab n'est sélectionné par défaut : le profil peut rester non configuré, choisir une école, choisir « sans préférence » ou demander la comparaison des quatre écoles. Une clarification est demandée lorsqu'une réponse dépend de ce choix.

Schéma conceptuel `fiqh_opinion` :

```text
opinion_id
question_concept_id
madhhab_id
scholar_id
work_id
edition_id
position_summary
conditions[]
exceptions[]
context_notes
source_passage_ids[]
review_status
reviewer_ids[]
valid_from / valid_until (si nécessaire)
```

Un comité éditorial savant doit approuver la liste des ouvrages, éditions et autorités retenus. Les exemples d'ouvrages classiques ne valent pas sélection officielle tant qu'ils n'ont pas été examinés par les réviseurs du projet.

## 5.3 Provenance et confiance documentaire

Chaque élément conserve :

- origine primaire et URL/identifiant bibliographique ;
- auteur ou attribution et degré de certitude de cette attribution ;
- titre de l'œuvre et édition exacte ;
- éditeur, traducteur, réviseur et volume/page lorsque disponibles ;
- langue source et langue d'affichage ;
- licence de l'original, licence de la transcription et autorisation de redistribution ;
- empreinte SHA-256 du fichier reçu et du texte source ;
- transformation appliquée (OCR, normalisation, correction) ;
- version de traitement et identité du réviseur ;
- statut de validation et date de dernière revue.

Le statut de validation documentaire ne signifie pas que toute interprétation ultérieure est incontestable.

## 5.4 Licences : condition préalable à toute distribution

La page officielle Tanzil autorise la copie et la redistribution verbatim de son texte sous CC BY 3.0, exige l'attribution et un lien vers Tanzil, et interdit de modifier le texte. Le pipeline doit conserver le texte original et placer les formes normalisées de recherche dans des champs dérivés séparés. [R12]

OpenITI indique que ses releases sont sous CC BY-NC-SA 4.0 ; cette licence contient une restriction NonCommercial et ne convient donc pas à une redistribution commerciale sans autorisation distincte. [R13]

Sunnah.com indique ne pas autoriser le scraping ni la reproduction massive de livres ou collections entières et renvoie vers son API pour les snapshots. Les conditions de l'API doivent être auditées séparément avant utilisation. [R14]

IslamicRAG-7M annonce 7 823 548 lignes et 33,3 Go. La déclaration de licence d'un dataset agrégé ne prouve pas, à elle seule, les droits sur chacune des sources sous-jacentes, les traductions et les embeddings. Ce dataset est candidat de recherche, pas corpus commercial approuvé. [R15]

**BLOQUANT-LEGAL-01 :** aucun texte, traduction, OCR, embedding distribué ou dérivé substantiel ne peut entrer dans un Corpus Pack tant que le dossier de droits de chaque source n'est pas approuvé.

## 5.5 Rôles de validation

- Responsable de collection : valide provenance, édition, intégrité et licence.
- Réviseur spécialiste : valide les métadonnées et annotations dans son domaine.
- Réviseur madhhab : confirme l'attribution des avis et des conditions.
- Réviseur hadith : confirme les références et classifications avec attribution.
- Responsable publication : accepte ou rejette la release documentaire.

Une validation d'un hadith n'autorise pas un réviseur à valider automatiquement un calcul d'héritage ou un avis de Halal.

# 6. Ingestion, chunking et formats de corpus

## 6.1 Pipeline documentaire hors téléphone

L'ingestion est un processus de préparation contrôlé, exécuté sur un poste de maintenance ou dans un environnement CI de préparation si la licence et les ressources le permettent. Ce n'est pas un backend utilisateur.

Étapes obligatoires :

1. Import de la source et enregistrement de la provenance.
2. Vérification de licence et de redistribution.
3. Parsing selon le format d'origine.
4. Contrôle d'intégrité et détection de doublons.
5. Normalisation de recherche, sans modifier l'original.
6. Structuration et chunking sémantique.
7. Annotation des références et relations parent/enfant.
8. Embeddings avec modèle/version/configuration figés.
9. Construction des index lexicaux et vectoriels.
10. Tests de cohérence, retrieval et citations.
11. Revue savante selon le type de collection.
12. Génération du manifeste, signatures et empreintes.
13. Publication d'un Corpus Pack immuable.

## 6.2 Règles de chunking

| Type | Unité primaire | Contexte parent |
|---|---|---|
| Coran | Ayah ou groupe contigu d'ayahs | Sourate, rasm/édition, traduction alignée |
| Hadith | Hadith complet et variantes identifiées | Livre, chapitre, collection, édition, isnad et matn |
| Tafsir | Passage commentant un verset ou groupe précis | Paragraphe, section, volume/page et ayah liée |
| Fiqh | Passage argumentatif conservant ses conditions | Chapitre, section, avis attribué et contexte adjacent |
| Cours savant | Segment cohérent avec repère temporel | Enregistrement, date, auteur, contexte et autorisation |

Le chunk récupéré n'est pas forcément le passage affiché. Le lecteur doit pouvoir remonter au parent et montrer le contexte adjacent.

### 6.2.1 Règles normatives par type documentaire

**Coran**

- L'ayah canonique, identifiée par `qiraat/edition + surah + ayah + convention de numérotation`, est l'unité de référence immuable.
- L'index peut créer des fenêtres de retrieval de 1 ayah puis des fenêtres contiguës de 2 à 5 ayahs selon la requête ; chaque fenêtre pointe vers les ayahs canoniques et conserve leur ordre.
- Le texte arabe original n'est jamais concaténé à une traduction pour former une source ambiguë. Chaque traduction est un objet séparé lié à l'ayah et porte traducteur, édition, langue et licence.
- Le lecteur affiche l'ayah exacte et peut montrer le contexte de la sourate. Le modèle ne peut pas citer une fenêtre si la citation n'est pas résolue à la/aux ayah(s) correspondante(s).

**Hadith**

- L'enregistrement canonique conserve collection, édition, livre, chapitre, numéro de cette édition, identifiant interne, isnad, matn, variantes et locateur.
- Isnad et matn peuvent être indexés séparément pour des requêtes spécialisées, mais le hadith complet reste le parent canonique.
- Les variantes ne sont pas fusionnées comme un seul texte ; elles sont reliées par `variantOf` ou une relation documentaire explicitement validée.
- Chaque jugement (sahih/hasan/da'if ou autre) est un `HadithGradeAssertion` distinct : classificateur, ouvrage, édition, localisation et citation justificative. Plusieurs jugements peuvent coexister ; aucun label global n'est inventé par l'index.

**Tafsir**

- Chunk par paragraphe/argument cohérent, avec relation explicite aux ayahs commentées.
- Conserver le titre de section, le volume, la page et les paragraphes adjacents nécessaires au sens.
- Un découpage automatique qui coupe une citation, une condition ou une chaîne argumentative doit être corrigé ou exclu du corpus publié.

**Fiqh**

- Chunk par question, avis ou argument attribué ; ne jamais isoler une conclusion des conditions, exceptions, prémisses ou contexte qui la limitent.
- Conserver séparément auteur, madhhab attribué, ouvrage, édition, position, conditions, exceptions, désaccords intra-école et preuves citées.
- Un résumé généré peut être indexé seulement s'il est marqué `DERIVED_SUMMARY`, lié au passage source, attribué à son générateur/réviseur et jamais présenté comme le texte original.

**Cours et médias**

- Segmenter avec timecodes, intervenant et contexte de cours ; conserver une transcription comme dérivé distinct de l'enregistrement.
- Le droit de transcription, de redistribution audio et de création d'embeddings doit être audité séparément.

### 6.2.2 Métadonnées obligatoires et états séparés

Chaque passage indexable expose un noyau commun : `sourceId`, `documentId`, `editionId`, `passageId`, `parentId`, `sourceType`, `traditionScope`, `madhhabRelations[]`, `languageTag`, `locator`, `originalTextHash`, `publicationVersion`, `sourceStatus`, `reviewStatus`, `licenseStatus`, `createdAt` et `updatedAt`.

Les métadonnées spécifiques ne sont pas aplaties dans un champ générique : classification de hadith avec son auteur et sa référence ; versets reliés pour les tafsirs ; auteur, avis, conditions et exceptions pour le fiqh.

Ne pas utiliser un seul `confidence` qui mélangerait qualité de provenance, exactitude bibliographique, pertinence de retrieval et validité d'interprétation. Ces dimensions restent séparées :

- `provenanceStatus` : provenance inconnue, déclarée, vérifiée ;
- `bibliographicStatus` : édition/locateur vérifié ou incomplet ;
- `reviewStatus` : non relu, revu par responsable de collection, relu par spécialiste, approuvé pour publication ;
- `licenseStatus` : non audité, autorisé, autorisation limitée, interdit ;
- `retrievalScore` : score technique relatif, jamais affiché comme vérité religieuse ;
- `claimSupportStatus` : relation entre un claim précis et ses preuves.

Les filtres durs excluent les documents hors périmètre, sans droits, non publiables ou mal formés. Le madhhab agit principalement comme préférence de classement et de présentation des avis interprétatifs ; les sources primaires ne sont pas supprimées mécaniquement.


## 6.3 Original et index de recherche

Les champs sont strictement distincts :

- `original_text` : immuable, exact et attribué ;
- `search_text_ar` : représentation normalisée pour retrieval ;
- `search_text_en` : texte anglais indexé ;
- `transliteration` : facultatif et versionné ;
- `translation_text` : traduction identifiée avec son auteur/édition.

Normalisations arabes possibles : retrait d'éléments diacritiques dans un champ de recherche dédié, variantes d'alif, alif maqsura/ya et tatweel selon une politique testée. Ces opérations ne doivent jamais remplacer le texte source ni fusionner des formes qui changent le sens. Le tokenizer FTS5 `unicode61` ne constitue pas un analyseur morphologique arabe ; une stratégie dédiée doit être validée avec des requêtes arabes réelles. [R16]

## 6.4 Corpus Pack : structure normative

Format recommandé : archive ZIP sans exécution de code, contenant des fichiers de données et un manifeste signé.

```text
<pack-id>-<version>.tpack
  manifest.json
  manifest.sig
  LICENSES/
    <license-id>.txt
  ATTRIBUTIONS.md
  CHANGELOG.md
  provenance.jsonl
  knowledge.sqlite
  vectors/
    vectors.meta
    vectors-000.bin
  evaluation/
    checksums.json
```

`knowledge.sqlite` contient les tables documentaires, métadonnées, relations parent/enfant et index FTS. Les vecteurs et l'index ANN restent des artefacts distincts et reconstruisibles.

### Manifeste minimal

```json
{
  "schemaVersion": 1,
  "packId": "sunni-core-ar",
  "packVersion": "1.0.0",
  "traditionScope": "SUNNI_FOUR_MADHHABS",
  "languages": ["ar", "en"],
  "createdAt": "2026-11-04T00:00:00Z",
  "minAppVersion": "1.0.0",
  "documents": 0,
  "passages": 0,
  "sourceRegistryVersion": "1.0.0",
  "embedding": {
    "modelId": "embeddinggemma2-text",
    "modelRevision": "PINNED_REVISION",
    "dimensions": 768,
    "normalization": "L2",
    "quantization": "INT8"
  },
  "files": [
    {"path": "knowledge.sqlite", "size": 0, "sha256": "..."},
    {"path": "vectors/vectors-000.bin", "size": 0, "sha256": "..."}
  ],
  "licenseIds": ["..."],
  "signingKeyId": "corpus-release-2026",
  "signingKeyEpoch": 1,
  "signatureAlgorithm": "Ed25519"
}
```

Les compteurs et valeurs sont des exemples de forme, pas des valeurs de release.

### Signature

- Algorithme de signature de release : Ed25519.
- Clé privée de publication conservée hors du dépôt ; accès protégé et journalisé.
- Vérificateur Ed25519 fourni par un provider dont la compatibilité avec le `minSdk` final est démontrée. Ne pas présumer que le provider cryptographique de chaque version Android expose uniformément Ed25519 ; le choix entre implémentation embarquée et provider système fait l'objet d'un spike et de tests connus. [R32]
- Clé publique de confiance intégrée dans l'application.
- Chaque clé a un identifiant et un epoch ; le keyring embarqué accepte uniquement les clés actives. Une révocation/rotation de clé est distribuée par une mise à jour signée de l'application ou par un mécanisme de confiance déjà établi ; un pack ne peut pas s'auto-déclarer une nouvelle clé de confiance.
- Chaque fichier possède une empreinte SHA-256.
- Vérifier la signature avant extraction, puis taille, chemin, hash, schéma, compatibilité et licence.
- Aucun code exécutable, script, plugin ou prompt système n'est autorisé dans le pack.

### Import sécurisé

1. Sélection SAF par l'utilisateur.
2. Copier vers une zone temporaire privée.
3. Vérifier taille maximale, chemins, compression, nombre de fichiers et absence de traversal ZIP.
4. Vérifier signature et hashes avant ouverture de la base.
5. Contrôler le schéma et la compatibilité.
6. Vérifier que la liste de licences est acceptée par l'application.
7. Installer dans un répertoire versionné.
8. Effectuer les tests d'intégrité.
9. Activer la version par transaction/commutation de pointeur.
10. Garder le pack précédent jusqu'à validation de l'ouverture et rollback possible.

Une interruption pendant l'import ne doit jamais laisser le pointeur actif vers un pack incomplet.

# 7. RAG hybride et validation des preuves

## 7.1 Pipeline de récupération

| Étape | Traitement | Sortie / invariant |
|---|---|---|
| 1. Compréhension | Détecter langue, intention, domaine, références exactes et madhhab explicitement demandé. | Requête typée ; aucune école n'est présélectionnée. |
| 2. Recherche exacte | Résoudre identifiants, ayahs, collections, éditions et locateurs connus par index structurés. | Résolution bibliographique sans génération. |
| 3. Recherche parallèle | Lancer FTS5/BM25 et recherche dense locale sur le même périmètre documentaire autorisé. | Candidats avec rang, méthode et provenance. |
| 4. Fusion | Fusionner les rangs par RRF et dédupliquer les mêmes passages/parents. | Classement hybride traçable. |
| 5. Filtrage | Appliquer les exclusions dures : portée sunnite, licence, statut de publication, provenance minimale, langue et domaine. Appliquer le madhhab comme filtre seulement lorsque la requête l'exige ; sinon comme préférence de présentation/classement. | Aucun document interdit ou hors périmètre dans le bundle. |
| 6. Re-ranking conditionnel | Exécuter le cross-encoder seulement si la politique versionnée le demande et si le budget mémoire/latence le permet. | Classement affiné ou fallback RRF-only explicite. |
| 7. Expansion contextuelle | Charger le parent, les ayahs adjacentes, les variantes et conditions nécessaires. | Passages contextualisés sans perdre le locator exact. |
| 8. Contrôle de couverture | Identifier doublons, contradictions, avis divergents et informations manquantes. | État de couverture et éventuels motifs d'abstention. |
| 9. Construction des preuves | Créer un `EvidenceBundle` versionné avec passages exacts, hashes, provenance et relations à la question. | Seuls les IDs de ce bundle peuvent être cités par le modèle. |

## 7.2 Choix des moteurs de recherche

- Recherche lexicale : SQLite FTS5 avec BM25, construite avec une version SQLite contrôlée.
- Recherche exacte : identifiants, numéros, ayah, collection et références bibliographiques par index B-tree.
- Recherche dense : abstraction `VectorIndex`.
- Fusion : Reciprocal Rank Fusion implémentée dans notre couche de retrieval.
- Re-ranking : modèle cross-encoder uniquement si le gain qualité/latence/mémoire est démontré.

`sqlite-vec` effectue une recherche vectorielle exacte ; ne pas le décrire comme un moteur HNSW. Un index ANN peut être ajouté derrière `VectorIndex` si les benchmarks le justifient. L'index vectoriel n'est jamais l'unique voie de recherche. [R17][R31]

Le premier benchmark doit comparer :

1. FTS5 + exact dense quantifié ;
2. FTS5 + HNSW ;
3. FTS5 + exact dense + re-ranking ;
4. FTS5 + HNSW + re-ranking.

Le choix doit être fondé sur recall, NDCG, latence P50/P95, RAM, stockage, énergie, temps d'indexation et coût de reconstruction.

## 7.3 Filtres

Les filtres comprennent : tradition, madhhab demandé, type de source, collection, langue, époque, édition, classification de hadith attribuée, statut de validation et droit de distribution.

Les sources primaires communes ne sont pas supprimées simplement parce qu'une école est sélectionnée. Le paramètre madhhab sert à orienter la recherche et la présentation des avis interprétatifs.

Les filtres sont séparés en deux familles :

- **Filtres d'éligibilité durs** : portée sunnite du produit, licence/redistribution, intégrité, statut de publication, langue réellement disponible et compatibilité d'édition. Ils s'appliquent avant l'assemblage final des preuves.
- **Préférences de recherche** : madhhab choisi, langue préférée, époque, auteur, type de commentaire. Elles influencent le classement et la présentation, mais ne suppriment pas une divergence pertinente ni une source primaire commune.

Si le profil de l'utilisateur n'a pas de madhhab configuré, le système ne doit pas en inventer un. Il peut demander une clarification ou afficher des vues distinctes selon les quatre écoles lorsque le corpus le permet.

### 7.3.1 Contrats Kotlin du retrieval

```kotlin
data class RetrievalRequest(
    val query: String,
    val languageTag: String,
    val requestedMadhhab: SunniMadhhab?,
    val sourceTypes: Set<SourceType>,
    val domain: KnowledgeDomain,
    val exactReference: ExactReference?,
    val riskClass: QueryRiskClass,
    val corpusSnapshot: CorpusSnapshot,
    val maxCandidates: Int
)

data class RankedPassage(
    val passageId: String,
    val parentId: String,
    val sourceId: String,
    val rank: Int,
    val retrievalMethod: RetrievalMethod,
    val rawScore: Double?,
    val metadata: PassageMetadata
)

data class RetrievalPolicyDecision(
    val mode: RetrievalMode,
    val reasons: Set<String>,
    val policyVersion: String
)
```

Ces contrats sont des DTO internes, versionnés. Les scores lexicaux et denses ne sont pas comparés directement sans normalisation ; RRF fusionne les rangs et conserve la provenance du candidat.


## 7.4 Embeddings

EmbeddingGemma 2 est candidat principal. Pour le RAG textuel, commencer les essais avec le modèle texte de 270M paramètres, puis comparer les dimensions 768, 512, 256 et 128. Le modèle doit être identique entre indexation et requêtes, même révision, instructions et prétraitement. [R05]

Les embeddings préexistants d'un dataset externe ne sont pas réutilisés avant identification exacte du modèle, de la révision, des instructions, de la normalisation et des droits associés. Deux embeddings de 768 dimensions ne sont pas interchangeables par leur seule taille.

## 7.5 Re-ranking adaptatif et mode dégradé

Le cross-encoder n'est **pas exécuté systématiquement**. Il augmente la latence, la pression mémoire et la consommation. `RerankerPort` est facultatif : l'absence de modèle, son échec, une pression mémoire ou thermique conduisent à un retrieval RRF-only.

**Politique initiale :**

- Référence exacte univoque (ayah, ID de hadith, référence bibliographique) : résolution exacte ; ne pas déclencher un cross-encoder par défaut.
- Requête courte/simple dont le retrieval lexical/dense converge et dont le gold-set confirme la précision : RRF seul.
- Requête paraphrasée, bilingue/translittérée, résultats proches ou forte ambiguïté de classement : re-ranking des top-K candidats si le modèle est disponible et dans le budget.
- Questions de fiqh avec divergence, héritage, Halal ambigu ou autre domaine à enjeu élevé : re-ranking et contrôles de couverture peuvent être obligatoires selon le profil validé ; si ces composants ne sont pas disponibles ou si les preuves sont insuffisantes, abstention ou renvoi vers revue, jamais réponse dégradée présentée comme équivalente.

Les signaux de déclenchement (marge entre candidats, désaccord lexical/dense, langue croisée, domaine, qualité de provenance) sont calibrés sur un corpus de validation distinct du jeu de test. Aucun seuil numérique n'est inventé avant cette calibration. Un score de cross-encoder reste un score de pertinence, pas une probabilité de vérité.

BGE Reranker v2 M3 est une piste de recherche, pas un choix de production. Le benchmark doit comparer au moins un candidat compact ou quantifié à l'absence de reranker. Les licences non commerciales, dont CC BY-NC, sont exclues d'une release commerciale sans autorisation distincte. Le modèle retenu doit être mesuré en arabe et anglais sunnites sur appareil réel : P50/P95, RAM/PSS, température, énergie, stockage et amélioration Recall/NDCG/claim-support.

### 7.5.1 Fusion RRF

Pour un document `d`, la fusion utilise une forme versionnée de :

`RRF(d) = Σ_i 1 / (k + rank_i(d))`

`k`, les poids éventuels, la stratégie de déduplication et les règles de normalisation sont stockés dans `retrievalPolicyVersion` et évalués sur le gold set. L'identité des passages et du document parent évite de compter plusieurs fois des variantes ou chunks quasi identiques comme des preuves indépendantes.

### 7.5.2 Contrat d'exécution conditionnelle

`RetrievalPlan` retourne `EXACT_ONLY`, `HYBRID_RRF`, `HYBRID_RRF_RERANKED` ou `ABSTAIN`. Le plan explique en audit les raisons du reranking ou de son absence. La panne du reranker ne peut jamais déclencher un fallback qui supprime des filtres de licence, de statut documentaire ou de portée religieuse.

## 7.6 Construction du paquet de preuves

Chaque candidat devient une preuve avec : passage exact, contexte parent, provenance, édition, langue, statut de source, relations à la question et éventuelles opinions contradictoires.

L'assemblage doit éviter :

- citations d'un passage sans son contexte déterminant ;
- duplication artificielle de la même source sous plusieurs URL ;
- présentation d'un avis isolé comme consensus ;
- omission d'une divergence détectée ;
- mélange de traduction et texte original ;
- classification de hadith sans attribution.

## 7.7 Génération et validation de claims

Le modèle génère des claims structurés qui ne peuvent référencer que les IDs du `EvidenceBundle` fourni. Un validateur post-génération vérifie :

1. que l'ID de source existe dans le pack actif ;
2. que le passage et le hash correspondent ;
3. que l'édition et le locateur sont résolus ;
4. que la citation affichée est un extrait exact ;
5. que le claim dispose d'une preuve ou est marqué comme explication/hypothèse ;
6. que les contradictions importantes n'ont pas été omises ;
7. que le niveau de réponse correspond au niveau de preuve.

Une simple similarité sémantique ne valide pas une affirmation. Un petit entailment/cross-encoder peut aider au triage, mais ne remplace pas la revue savante et ne produit pas à lui seul une vérité religieuse.

### 7.7.1 Niveaux de validation des citations

Les niveaux suivants sont distincts et ne doivent pas être confondus :

1. `REFERENCE_RESOLVED` : l'identifiant de source/édition/locateur existe dans le pack signé actif.
2. `QUOTE_EXACT` : le texte entre guillemets correspond aux octets/caractères du passage canonique après les règles d'affichage explicitement documentées ; il est extrait du store, jamais réécrit par le LLM.
3. `CLAIM_EVIDENCE_LINKED` : le claim référence un `evidenceId` présent dans le bundle et le validateur a vérifié la relation documentaire attendue.
4. `SCHOLAR_REVIEWED` : une personne compétente a révisé ce contenu précis, avec domaine, identité éditoriale, version et date.

Les trois premiers niveaux ne signifient pas que l'interprétation est validée religieusement. Une source exacte peut être mal interprétée. L'interface ne montre « revue savante » que si le quatrième niveau existe et couvre la version/claim concerné.

La sortie du modèle suit un JSON Schema strict : claims, type de claim, texte, `evidenceIds`, relation proposée et limites. Les IDs doivent appartenir au `EvidenceBundle` courant. Toute référence inconnue, locator non résolu, hash incorrect ou citation textuelle qui ne correspond pas au passage est rejeté. L'application récupère elle-même le passage original et construit le lien/lecteur.

Pour une paraphrase, le système peut effectuer un contrôle de support sémantique et signaler un cas à revoir ; il ne peut pas convertir automatiquement ce contrôle en « preuve de vérité ». Les réponses à fort enjeu qui ne disposent que d'un soutien ambigu sont `NEEDS_REVIEW` ou `INSUFFICIENT`.

### 7.7.2 Invariants d'abstention

- Aucune source éligible : `INSUFFICIENT`.
- Passage retrouvé mais traduction ou édition non vérifiée : afficher la limite et ne pas présenter le texte dérivé comme canonique.
- Avis de madhahib divergents : conserver et présenter la divergence ; ne pas fabriquer un consensus.
- Grade de hadith conflictuel ou sans attribution suffisante : afficher les classifications attribuées ou s'abstenir.
- Sources contradictoires non résolues : ne pas forcer une réponse unique.
- Corpus pack trop ancien ou statut de fraîcheur inconnu pour une donnée temporelle : avertir et demander import/mise à jour.


### Statuts utilisateur

| Statut | Signification | Affichage |
|---|---|---|
| `SUPPORTED` | Sources vérifiées soutiennent les claims affichés | Références consultables |
| `PARTIALLY_SUPPORTED` | Certaines affirmations sont documentées, d'autres limitées | Limites visibles |
| `DISAGREEMENT` | Sources ou écoles présentent des avis différents | Présentation des avis séparés |
| `INSUFFICIENT` | Preuves trop faibles ou données manquantes | Abstention ou question |
| `OUT_OF_SCOPE` | Sujet hors périmètre/documentation | Explication de la limite |
| `NEEDS_REVIEW` | Cas complexe ou à enjeu élevé | Escalade/revue nécessaire |

Aucune réponse « sûre à 95 % » n'est produite à partir de la seule confiance du modèle.

# 8. Génération locale et gestion des modèles

## 8.1 Runtime et abstraction de fournisseur

LiteRT-LM Kotlin est le runtime candidat pour exécuter les poids Gemma 4 E2B/E4B importés et épinglés. Google documente leur prise en charge et publie des mesures Android. Gemma 4 est sous Apache 2.0. [R03][R04]

La fiche de modèle LiteRT Community indique également qu'Android AI Core / Gemini Nano est le chemin recommandé pour la production sur les appareils Android compatibles. Ce service système est une option locale distincte : il ne faut pas supposer qu'il expose exactement le même fichier E2B/E4B, la même révision, le même comportement multimodal ou les mêmes possibilités de contrôle que le poids LiteRT-LM épinglé. [R35]

Le contrat `OnDeviceInferenceProvider` doit permettre d'évaluer séparément :

- `LiteRtGemmaProvider` : poids `.litertlm` explicites, versions contrôlées par l'application ;
- `AndroidAiCoreProvider` : modèle système disponible et versionné par l'environnement Android, uniquement sur appareils compatibles.

Le spike doit comparer disponibilité réelle, fonctionnement hors ligne, données effectivement traitées localement, modalités, tool calling manuel, quotas d'exécution, stabilité API, latence, mémoire, consommation et couverture des appareils. Si AI Core n'expose pas un modèle/version compatible avec la promesse documentaire, il ne remplace pas automatiquement LiteRT-LM. Aucune fonction métier ne dépend du type de fournisseur.

EmbeddingGemma 2 texte (270M) est le candidat pour embeddings textuels. Ses encodeurs vision/audio sont optionnels ; l'architecture ne charge pas les trois modalités lorsque le produit ne les utilise pas. [R05]

## 8.2 Model Manager

Le gestionnaire de modèles doit :

- importer un fichier via SAF ;
- vérifier nom, taille, hash, provenance, licence, version et format ;
- vérifier que le runtime sait l'ouvrir ;
- contrôler espace disque disponible ;
- charger à la demande ;
- empêcher deux gros modèles de rester chargés sans nécessité ;
- décharger en cas de pression mémoire/thermique ;
- exposer l'état à l'utilisateur ;
- supprimer un modèle sans effacer ses données utilisateur ;
- ne jamais télécharger ou remplacer silencieusement un modèle.

Pour AI Packs, le gestionnaire interroge `getPackStates()`/`getPackLocation()` à chaque lancement et suit explicitement les statuts de livraison ; il ne persiste pas un ancien chemin d'asset pack comme s'il était garanti.

Les modèles de plusieurs Go ne sont pas intégrés au base APK. Le modèle peut être absent : l'application reste fonctionnelle avec règles et retrieval lexical/dense disponible selon les modèles installés.

### 8.2.1 Profil matériel, mémoire et préflight E4B

Aucun seuil marketing tel que « 8 Go de RAM » ou « 7 Go utilisables » n'est accepté comme règle universelle. La mémoire disponible dépend de l'appareil, de la version Android, des applications concurrentes, du backend CPU/GPU/NPU, de la quantification, du contexte, des caches et du runtime.

Avant d'activer un modèle, le `ModelPreflight` collecte au minimum : `ActivityManager.MemoryInfo` (`totalMem`, `availMem`, `lowMemory`), `ActivityManager.MemoryClass`, mémoire du processus (PSS/RSS), espace libre et espace temporaire, backend réellement sélectionné, paramètres/context length, version du runtime, état thermique disponible et résultat d'un chargement/essai contrôlé sur une entrée représentative. Les données API/SoC et RAM annoncée sont seulement des signaux préliminaires.

Le gestionnaire attribue un profil local à chaque couple appareil/OS/runtime/modèle/backend :

- `MODEL_READY` : préflight complet réussi avec marge suffisante selon les mesures validées ;
- `MODEL_CONSTRAINED` : fonctionnement possible avec contexte/taille de batch réduits, sans tâche concurrente lourde ; avertissement visible ;
- `MODEL_UNAVAILABLE` : préflight échoué, OOM, pression mémoire/thermique ou espace insuffisant ; le modèle n'est pas proposé comme disponible.

Le résultat est réévalué après mise à jour du modèle/runtime, changement de backend ou échec mémoire. E4B est chargé à la demande, un seul gros modèle génératif reste actif à la fois par défaut, et l'utilisateur peut choisir E2B ou le mode sans LLM. Aucun chargement forcé d'E4B ne bloque la première ouverture. Les seuils de marge sont établis sur un parc d'appareils représentatif, par modèle/runtime/backend, puis versionnés dans le profil de compatibilité. [R38]

## 8.3 Processus d'inférence

RECOMMANDÉ : exécuter le runtime dans un processus Android lié dédié (`:inference`) lorsque le spike technique confirme la compatibilité du runtime et le bénéfice de l'isolation. L'interface utilise un contrat Binder/AIDL ou un client de service interne. Le processus doit pouvoir être tué et relancé sans perdre la tâche, puisque le Task Repository est la source de vérité.

Si ce coût IPC dépasse le bénéfice, une exécution dans le processus principal est acceptable à condition de borner mémoire, annulation, durée et thread pools et d'assurer la gestion des crashes natifs.

## 8.4 Profils de modèle

| Profil | Modèle candidat | Usage | Limites |
|---|---|---|---|
| Réponse courte | E2B | Reformulation, explication guidée, proposition courte | Ne pas l'appeler always-on |
| Raisonnement plus lourd | E4B | Canvas multi-étapes | Chargement à la demande, coût mémoire supérieur |
| Embedding texte | EmbeddingGemma 2 Text | Query et indexation | Test de qualité arabe/anglais obligatoire |
| Vision/OCR | Modèle spécialisé à sélectionner | Pages, étiquettes et documents | Pas de validation religieuse par le modèle |
| Audio/ASR | Modèle spécialisé à sélectionner | Récitation/voix | Segmenter les longs audios ; pas d'évaluation tajwid automatique sans validation |
| Re-ranking | Non sélectionné | Reclasser les candidats | Benchmark qualité/coût/licence |

## 8.5 Tool calling manuel obligatoire

LiteRT-LM permet l'exécution manuelle des outils en configurant `automaticToolCalling = false`. La production doit obligatoirement utiliser ce mode. [R06]

Le modèle peut proposer un appel d'outil ; l'application interprète la proposition, valide le schéma, évalue l'autorisation et n'appelle l'exécuteur qu'après une décision favorable. Aucun callback automatique fourni par le runtime ne peut réaliser une action externe ou modificatrice.

# 9. Orchestration agentique

## 9.1 Principe

L'agent est événementiel et non un processus de surveillance permanente.

Sources d'événements :

- action explicite de l'utilisateur ;
- notification/alarme planifiée par l'application ;
- événement du calendrier après permission ;
- changement des préférences ;
- saisie manuelle de voyage ;
- résultat d'un moteur de domaine ;
- action d'import de fichier ou de photo.

L'agent ne déduit pas silencieusement maladie, fatigue, pratique religieuse ou état familial.

## 9.2 Cycle d'une tâche

```text
EVENT_RECEIVED
  -> CONTEXT_SNAPSHOTTED
  -> INTENT_CLASSIFIED
  -> PLAN_CREATED
  -> EVIDENCE_RETRIEVED (si nécessaire)
  -> DOMAIN_COMPUTED (si nécessaire)
  -> CLAIMS_VALIDATED
  -> PROPOSAL_CREATED
  -> CARD_PUBLISHED
  -> WAITING_FOR_USER (si action modificatrice)
  -> AUTHORIZATION_CHECKED
  -> TOOL_EXECUTION (si approuvé)
  -> OUTCOME_VERIFIED
  -> AUDITED
  -> COMPLETED
```

Un résultat d'analyse sans action peut se terminer après validation et publication. Un tool call mutatif ne saute jamais l'autorisation.

## 9.3 Machine à états AgentTask

```text
CREATED -> RUNNABLE -> RUNNING
RUNNING -> CHECKPOINTED -> RUNNABLE
RUNNING -> WAITING_FOR_INPUT
RUNNING -> WAITING_FOR_APPROVAL
RUNNING -> INTERRUPTED -> RESUMABLE -> RUNNING
RUNNING -> COMPLETED

États terminaux alternatifs : CANCELLED, EXPIRED, FAILED, ABSTAINED
```

`INTERRUPTED` est un constat de cycle de vie, pas un état terminal. `CHECKPOINTED` indique qu'un état cohérent a été écrit avant le prochain travail. Les transitions sont persistées avec `stateVersion`, un numéro de séquence et un horodatage. Une reprise vérifie le dernier état et les préconditions ; un état ancien ne déclenche jamais automatiquement une action.

## 9.4 Planificateur

Le planificateur produit des étapes typées, pas un script arbitraire. Il n'exécute pas de code généré par le modèle. Chaque étape fait référence à un Use Case enregistré dans le Tool Registry ou à une opération pure.

Les opérations de calcul sont déterministes et réexécutables. Les tâches qui dépendent d'un utilisateur attendent explicitement son intervention sans conserver de thread ou service permanent.

## 9.5 Pas de framework agentique serveur

La cible est une orchestration Kotlin explicite. LangChain4j, LangGraph4j, Spring Boot et un serveur Java ne sont pas requis pour l'application Android. Un moteur de transitions persistant et des Use Cases Kotlin donnent une maîtrise plus directe du cycle de vie Android, des autorisations et des reprises. Toute dépendance future doit démontrer sa compatibilité Android, sa taille, sa licence et sa valeur par rapport à cette orchestration.

## 9.6 Protocole opérationnel de reprise

WorkManager sait conserver et replanifier des travaux persistants à travers des redémarrages ; il ne remplace pas notre état de tâche métier. [R37]

Au démarrage ou à la reprise de l'application :

1. Charger les tâches non terminales depuis Room.
2. Identifier les leases actifs et expirés ; une tâche sans lease valide ne peut pas être considérée comme encore exécutée.
3. Vérifier la dernière transition persistée et le `checkpointVersion`.
4. Vérifier les versions nécessaires (corpus, modèle, règle de domaine, schéma du Canvas).
5. Marquer `RUNNING` sans lease comme `INTERRUPTED`, puis `RESUMABLE` si le checkpoint est compatible.
6. Restaurer `WAITING_FOR_INPUT` et `WAITING_FOR_APPROVAL` comme UI persistée, sans déclencher automatiquement un outil.
7. Réconcilier l'outbox pour tous les effets de bord qui ont pu atteindre un provider externe.
8. Programmer seulement les étapes locales qui sont sûres et éligibles.

Les workers sont uniques par tâche/étape et le contenu de `Data` WorkManager ne contient que des IDs non sensibles. Les retries sont limités et les backoff sont choisis par classe d'erreur. Une erreur permanente ou un état externe ambigu n'est pas relancé automatiquement.

## 9.7 Canvas persistant et changement de versions

Un Canvas est une projection de `canvas_session`, `canvas_step`, `canvas_snapshot` et des artefacts associés. L'état sauvegardé comprend les champs saisis, paramètres choisis, hypothèses, preuves exactes, résultats des moteurs déterministes, actions proposées, décisions utilisateur et versions de règles/corpus/modèles.

Les changements acceptés et les transitions de jalon sont persistés transactionnellement. L'interface peut utiliser un debounce pour les caractères saisis, mais une navigation hors Canvas ne doit pas perdre une modification déjà validée par l'utilisateur.

Si une mise à jour de corpus ou de moteur rend un résultat ancien : le Canvas conserve l'instantané original, affiche qu'il a été calculé avec les versions antérieures et propose un recalcul séparé. Aucun résultat historique n'est écrasé silencieusement.

# 10. Human Approval & Action Authorization Engine

## 10.1 Politique par défaut

| Classe d'action | Règle |
|---|---|
| Lecture locale sans effet de bord | Autorisée selon permission système et consentement |
| Calcul local pur | Autorisé ; résultat explicable |
| Proposition/draft non appliqué | Peut être créée et affichée |
| Création ou modification de données | Demander par défaut |
| Modification calendrier/rappel | Demander par défaut |
| Enregistrement de document ou montant financier | Demander par défaut |
| Suppression | Confirmation spécifique ; renforcement selon impact |
| Partage/export vers tiers | Confirmation spécifique avec prévisualisation |
| Transaction, engagement légal ou action irréversible | Confirmation individuelle obligatoire, jamais couverte par grant permanent |
| Action à portée inconnue | Refuser ou demander une clarification, jamais autoriser par défaut |

## 10.2 Trois décisions de politique et une confirmation renforcée

- `ASK_EACH_TIME` : approbation pour chaque proposition.
- `ALLOW_WITHIN_GRANT` : exécution automatique uniquement si le grant correspond exactement au type, à la cible, aux paramètres et aux bornes.
- `DENY` : action interdite.
- `ALWAYS_CONFIRM_STRONG` : confirmation par action et écran renforcé ; impossible à remplacer par un grant générique.

Le réglage permanent ne doit jamais être présenté comme « l'agent peut gérer X ». Il doit décrire l'action et ses contraintes concrètes.

## 10.3 Modèle de permission

```kotlin
data class PermissionGrant(
    val grantId: String,
    val actionType: String,
    val targetScope: String,
    val constraintsCanonicalJson: String,
    val maxMagnitude: String?,
    val allowedTimeWindow: String?,
    val validFrom: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val policyVersion: Int,
    val createdByExplicitUserAction: Boolean
)
```

Les wildcards de type `*`, les scopes illimités et les paramètres non bornés sont interdits pour les grants d'action. Toute permission est révocable immédiatement.

## 10.4 Machine à états d'approbation

```text
PROPOSED
  -> POLICY_EVALUATED
      -> DENIED
      -> AWAITING_APPROVAL
          -> APPROVED
          -> REJECTED
          -> EXPIRED
      -> GRANT_MATCHED
          -> READY_TO_EXECUTE

APPROVED / GRANT_MATCHED
  -> PRECONDITIONS_RECHECKED
  -> EXECUTING
      -> SUCCEEDED
      -> FAILED
      -> PARTIALLY_APPLIED
  -> VERIFIED
  -> AUDITED
```

À tout moment avant le commit : `REVOKED`, `CANCELLED` ou `STALE_PROPOSAL` bloque l'exécution.

## 10.5 Règles d'approbation

- L'approbation est attachée à `proposalId`, `parametersSha256`, `actionType`, `targetId`, `policyVersion` et une échéance.
- L'utilisateur voit l'état actuel et l'état proposé côte à côte.
- Le résumé indique les conséquences, données touchées, destination éventuelle et possibilité d'annulation.
- L'approbation est vérifiée à nouveau juste avant le commit.
- Tout changement de paramètre, de cible ou de version de ressource invalide l'approbation.
- Le double tap, une reprise après crash ou une répétition de callback ne doit pas créer un doublon.
- Les actions externes et sensibles exigent une nouvelle confirmation à chaque fois.

## 10.6 Exécution transactionnelle

Chaque action mutative dispose :

- d'une clé d'idempotence ;
- de préconditions ;
- d'une version de ressource attendue ;
- d'un résultat/reçu ;
- d'une stratégie de compensation/undo si possible ;
- d'un événement d'audit.

L'agent ne peut pas appeler `ToolExecutor` directement. Seul `AuthorizationGate` peut produire `ApprovedAction`, type non constructible en dehors du module authorization.

# 11. Moteurs de domaine

## 11.1 Prière

Le moteur de prière est déterministe. Il utilise date, fuseau, coordonnées ou ville saisie, méthode de calcul et paramètres explicites. Les paramètres pertinents incluent notamment la méthode de calcul, la règle Asr, les hautes latitudes et le mode de correction.

Les horaires calculés et les avis sur qasr/jam' sont deux types de résultats distincts. Un changement d'heure ne produit pas automatiquement une conclusion juridique sur la prière en voyage.

Tout changement de fuseau, de ville ou de méthode provoque une recalculation versionnée. La ville peut être saisie manuellement ; l'autorisation de localisation ponctuelle est facultative. Aucune localisation permanente n'est requise.

## 11.2 Hifz et Coran

Sous-systèmes :

- édition de Mushaf et index de pages ;
- référence exacte sourate/ayah ;
- plan de mémorisation ;
- révision espacée ;
- historique des passages ;
- reconnaissance de page ;
- reconnaissance vocale segmentée ;
- alignement texte/audio ;
- correction manuelle.

L'OCR d'une page ne devient jamais le texte coranique de référence. Il propose un emplacement ou un candidat qui doit être rapproché du corpus exact. Les diacritiques, signes, numéros d'ayah et la mise en page doivent être conservés.

La reconnaissance de voix n'est pas une évaluation du tajwid. Toute évaluation de récitation exige un modèle spécialisé et une validation par des enseignants qualifiés. À défaut, afficher uniquement une transcription/alignement avec limites explicites.

## 11.3 Halal

Le moteur distingue : ingrédient reconnu, origine inconnue, additif ambigu, certification identifiée, certificat expiré/non vérifié et données insuffisantes.

Un ingrédient ne suffit pas à produire une conclusion Halal/Haram indépendante du contexte. Les certifications doivent enregistrer organisme, territoire, produit/site concerné, date de validité, numéro et source. Une photo d'étiquette n'est pas une preuve de certification.

## 11.4 Zakat

Le moteur effectue des calculs déterministes à partir d'entrées confirmées. Les paramètres comprennent l'ensemble d'actifs applicable, dettes selon le cadre choisi, nisab, référence or/argent, date de valorisation, période et méthode retenue.

Les prix/valeurs ne sont pas prétendus actuels sans donnée importée et datée. Les champs extraits d'un relevé sont présentés à l'utilisateur avant leur inclusion dans le calcul. Chaque ligne de résultat doit être recalculable et explicable.

Le modèle présente les sources et hypothèses ; il ne décide pas seul de l'applicabilité d'un avis.

## 11.5 Héritage

Le moteur d'héritage est isolé et activé uniquement après revue juridique et savante. Il sépare : situation civile applicable, faits familiaux confirmés, règles de fiqh sélectionnées, calcul arithmétique et explication.

Il doit représenter les cas incomplets, les empêchements, les dettes, testaments/wasiyya, héritiers éventuels et divergences de règles. Aucun calcul ne doit prétendre être un avis juridique civil ni un partage définitif sans validation compétente.

## 11.6 Voyage

Le moteur local gère fuseau, itinéraire importé, heures locales, Qibla calculée localement et contenus de fiqh sélectionnés. Sans serveur, les retards de vol ne sont pas détectés en direct. L'utilisateur peut importer ou corriger manuellement les détails du trajet.

Les questions de qasr/jam' sont des résultats documentaires et de fiqh, pas une conséquence automatique du seul calcul de fuseau horaire.

## 11.7 Communauté locale

Sans backend, la fonctionnalité communautaire est un annuaire et/ou un agenda local importable et versionné. Les événements affichent leur date de dernière mise à jour et leur provenance. Pas de présence live, chat, notification communautaire distante ou signalement centralisé.

# 12. Temps, notifications et cycle de vie Android

- WorkManager sert aux travaux différables et persistants, pas à une boucle always-on.
- AlarmManager sert aux alertes de l'utilisateur nécessitant une heure précise, sous réserve de permission et de politique Play.
- Les alarmes sont recalculées au changement d'heure, de fuseau, de date, de méthode et après redémarrage.
- Les notifications sont désactivables et regroupées selon les préférences.
- Les propositions de l'agent ne sont pas des notifications urgentes par défaut.
- Aucun service au premier plan permanent n'est utilisé pour maintenir le modèle en mémoire.

WorkManager 2.12.0 est la version stable observée à la date de référence et relève son minSdk à API 24. Les alarmes exactes sont soumises à permission et à des critères d'usage ; ne pas choisir `USE_EXACT_ALARM` sans vérifier l'éligibilité Google Play. [R07][R18]

## 12.1 Fraîcheur et provenance des données contextuelles

Chaque donnée issue d'un import ou d'un pack transporte `sourceId`, `sourceVersion`, `importedAt`, `observedAt` lorsque pertinent, et `freshnessStatus`. L'interface affiche les informations suivantes lorsque nécessaires : « saisi par vous », « importé le… », « fourni par le pack… », « données anciennes » ou « fraîcheur inconnue ».

Une carte de vol ne peut afficher que les horaires du billet/itinéraire importé ou saisi. Elle ne peut pas annoncer qu'un vol est retardé sans source de mise à jour effectivement importée. Une carte communautaire indique la date de capture des événements. Une donnée de prix, de certification ou d'échéance de calcul ne doit pas être présentée comme actuelle à partir d'un pack statique ancien.

## 12.2 Réconciliation Android

Après changement d'heure système, de fuseau, de date, de profil de prière, de permissions ou après redémarrage : recalculer les prochains événements déterministes et reprogrammer les alarmes applicables. Les notifications en attente de décision restent en attente ; aucun changement d'action n'est implicitement autorisé par une alarme ou un Worker.

Les tâches agentiques lourdes et l'inférence de plusieurs secondes ne sont pas déclenchées en arrière-plan comme une veille permanente. WorkManager ne lance que les traitements bornés, justifiés par une action utilisateur ou un travail différable préalablement accepté.
# 13. Sécurité et confidentialité

## 13.1 Modèle de menace

Actifs à protéger : données religieuses, historique de prière, plans Hifz, données familiales, dossiers financiers, pièces jointes, audio, paramètres de madhhab, états d'approbation, clés de chiffrement, corpus et artefacts de modèle.

Menaces principales : appareil perdu, extraction locale, sauvegarde cloud involontaire, app compromise, injection de prompt par document, pack malveillant, exécution d'action non autorisée, fuite dans logs/crash, bibliothèque native compromise et partage accidentel.

## 13.2 Stockage

RECOMMANDÉ : Room pour les données utilisateur avec SQLCipher for Android comme candidat de chiffrement de la base. SQLCipher documente une intégration avec Room 2 et Room 3 et une compatibilité Android API 23+, mais l'exemple de dépendance ne suffit pas à certifier notre combinaison Room 2.8.5 / AndroidX SQLite 2.7.1. L'AAR, ses bibliothèques natives, les ABI et la licence doivent être inclus dans le spike et l'audit de dépendances. [R19]

- Clé DB aléatoire générée et protégée/enveloppée par Android Keystore.
- AES-GCM pour les pièces jointes et enveloppes de données sensibles.
- Nonce/IV unique à chaque chiffrement.
- Clés et secrets jamais écrits en clair dans DataStore, logs ou BuildConfig.
- BiometricPrompt peut protéger une action sensible ou l'ouverture d'un coffre si l'utilisateur l'active ; le verrouillage ne doit pas rendre les rappels essentiels inaccessibles sans raison.
- Les caches de modèles et index sont exclus de la sauvegarde utilisateur, sauf choix explicite.

Ne pas employer `EncryptedSharedPreferences` comme nouvelle fondation : les APIs AndroidX Security Crypto sont dépréciées au profit des APIs de plateforme et d'Android Keystore. [R20]

## 13.3 Sauvegarde et export

La configuration doit exclure la base privée, les attachments, les modèles, les clés et les corpus des sauvegardes automatiques par défaut. Les règles `dataExtractionRules` et `allowBackup` doivent être testées sur cloud backup et transfert appareil-à-appareil. [R21]

L'export de données utilisateur est une opération explicite : aperçu du contenu, chiffrement par mot de passe/clé choisie, destination SAF ou partage Android, confirmation individuelle et journal d'audit.

## 13.4 Logs et audit

- Aucun prompt complet ou document sensible dans Logcat.
- Pas de SDK analytics/ads dans la configuration de référence.
- Audit local tamper-evident avec séquence, hash de contenu et MAC protégé par Keystore.
- Ne pas prétendre à un journal immuable contre un appareil rooté ou un propriétaire qui efface l'application.
- L'utilisateur peut consulter, exporter et effacer les données et journaux selon la politique de conservation.

## 13.5 Protection contre l'injection de prompt

Les documents, OCR, métadonnées, textes importés et sorties d'outils sont traités comme des données, jamais comme des instructions système. Les outils disponibles sont une allowlist typée. Le LLM ne peut fournir ni code exécutable, ni classe, ni URI arbitraire, ni SQL, ni commande shell.

## 13.6 Référentiel de sécurité

Adopter OWASP MASVS 2.1.0 comme baseline d'audit, en couvrant stockage, cryptographie, authentification, réseau, plateforme, code, résilience et confidentialité. Ajouter revue de code native, scan de dépendances, SBOM, tests de sauvegarde et tests d'injection. [R22]

# 14. Langues, RTL et accessibilité

## 14.1 Langues

Langues de lancement : `ar` et `en`. Le code utilise des tags BCP 47 et des tables de traduction, sans `if/else` dispersés selon deux langues. Toute table documentaire possède `languageTag` et peut être étendue ultérieurement.

## 14.2 RTL et bidirectionnalité

- Déclarer `supportsRtl` et les langues de l'application.
- Utiliser les notions `start`/`end`, pas des marges gauche/droite codées en dur.
- Employer des isolates bidi ou APIs équivalentes pour insérer des IDs, dates, numéros ou noms latins dans une phrase arabe.
- Tester références arabes + chiffres + parenthèses + noms anglais.
- Ne jamais inverser le texte arabe comme une chaîne ; laisser le moteur bidi gérer l'ordre visuel.
- Tester TalkBack en RTL et LTR.

Android documente les problèmes de textes mixtes, le support RTL et les langues par application ; ils doivent être testés explicitement. [R23]

## 14.3 Accessibilité

- tailles de texte adaptatives ;
- contrastes conformes ;
- cibles tactiles accessibles ;
- libellés TalkBack ;
- navigation clavier/switch access quand applicable ;
- états d'approbation non codés uniquement par couleur ;
- alternative à swipe pour approuver/refuser ;
- transcription/commandes vocales affichées avant les actions ;
- contenu arabe lisible avec tailles et fontes adaptées.

# 15. Distribution des modèles et Corpus Packs — décision de production

## 15.1 Décision générale

La distribution est **hybride et conditionnelle** :

- Google Play reste le canal de distribution de l'application Android Play.
- Play for On-device AI (bêta) est évalué comme canal de livraison de modèles, avec distribution `on-demand` de préférence. Il ne devient pas une dépendance incontournable tant que le sharding et le chargement du `.litertlm` ne sont pas prouvés dans un test Play interne.
- Import SAF est le fallback de production pour les modèles et la voie de référence pour les Corpus Packs complémentaires.
- Les modèles et corpus restent des fichiers statiques signés/versionnés ; aucun serveur produit ne les génère ou ne les personnalise.

Cette décision respecte l'absence de backend applicatif, mais ne signifie pas que toute installation est sans connexion : la livraison Play et les téléchargements effectués dans un navigateur utilisent Internet. Les données utilisateur ne sont pas transmises à ces services.

## 15.1.1 Arbitrage des trois voies de distribution proposées

| Voie | Statut | Décision de production |
|---|---|---|
| Play for On-device AI | **PARTIEL / conditionnel** | Meilleur parcours intégré pour les appareils Play compatibles, mais les plafonds par AI Pack, le total AAB et l'état bêta doivent être respectés. Le modèle E2B peut être évalué en premier ; aucun sharding n'est considéré acquis. |
| Import manuel depuis un navigateur/ordinateur via SAF | **CORRECT / fallback garanti par conception** | Voie de repli et de secours indépendante d'un backend. L'application n'intègre pas de downloader HF ; elle valide localement le fichier choisi, le hash, le format, le manifeste et la licence. ADB est réservé au développement. |
| Hybride Play + SAF | **RETENU, sous conditions** | Play AI Delivery pour E2B uniquement si le spike passe ; SAF pour E4B et les corpus dans la stratégie de base. Si le spike échoue, SAF reste le mécanisme pour les deux modèles. Aucun corpus n'est placé dans un AI Pack. |

Le téléchargement Hugging Face par l'application n'est pas retenu : Hugging Face est un hébergeur tiers et exigerait un client réseau, une gestion de redirections/versions/quotas et une surface de confidentialité supplémentaire. Le parcours utilisateur recommandé est un téléchargement externe, suivi d'un import SAF. Cette solution respecte l'absence de backend du produit, mais elle n'est pas « sans Internet » au moment du téléchargement.

## 15.2 Play for On-device AI (bêta) : possibilités et limites

La documentation Google actuelle décrit des AI Packs install-time, fast-follow ou on-demand, la possibilité de cibler des appareils selon modèle, SoC ou RAM, et une livraison avec patching des ressources. Les packs sont mis à jour avec le binaire de l'application ; pour un pack on-demand/fast-follow, l'application vérifie sa disponibilité à chaque lancement et ne met pas en cache durablement un `AiPackLocation` entre lancements, car celui-ci peut devenir invalide après une mise à jour ou un effacement des données. Les packs téléchargés sont internes à l'application et réservés à son usage. Elle indique :

- 1,5 Go maximum par AI Pack, calculé sur la taille compressée téléchargée ;
- 4 Go de taille cumulée maximale pour toute version d'application générée depuis l'App Bundle selon la page spécifique Play for On-device AI ;
- AI Packs contenant uniquement des modèles : aucun code Java/Kotlin ou bibliothèque native ne peut y être inclus ;
- AI Delivery Library à intégrer pour l'on-demand/fast-follow ; la documentation consultée présente `0.1.1-alpha01`, à traiter comme une dépendance bêta/alpha et à revalider avant chaque release.

Sources officielles : [R33][R34]. Ne pas extrapoler automatiquement à AI Packs la limite générale de 30 Go mentionnée pour des asset packs on-demand/fast-follow dans la documentation générale Play : la page spécifique AI Packs énonce le plafond cumulé de 4 Go. Tant que Google ne clarifie pas explicitement l'interaction des quotas pour notre AAB, la règle prudente de conception est d'appliquer le plafond AI Pack de 1,5 Go et le plafond cumulé de 4 Go.

## 15.3 Taille des modèles et faisabilité du sharding

Les artefacts LiteRT-LM documentés par Google font environ 2,58 Go pour Gemma 4 E2B et 3,65 Go pour Gemma 4 E4B dans les variantes mesurées. Ce sont des tailles de fichiers publiées ; la limite Play AI Pack s'applique à la taille compressée téléchargée. Il ne faut donc pas déclarer mathématiquement qu'un fichier dépasse le quota compressé sans mesurer l'artefact réellement empaqueté. Comme il s'agit de poids quantifiés, une réduction suffisante par compression n'est pas présumée : la preuve doit venir du bundle/AAB et des outils Play. Les variantes GPU peuvent avoir une taille différente ; chaque build doit épingler le dépôt, le commit, le nom du fichier, la taille réelle, la taille empaquetée et son SHA-256. [R03][R35][R36]

**Hypothèse non validée :** découper un `.litertlm` en fragments statiques répartis dans plusieurs AI Packs, télécharger ces packs on-demand, reconstruire le fichier final dans le stockage privé, vérifier le SHA-256 final puis ouvrir le modèle avec LiteRT-LM. La documentation Play décrit des packs distincts mais ne documente pas explicitement ce workflow de sharding/réassemblage d'un modèle. Il ne doit donc pas être présenté comme une fonction officiellement garantie.

### Spike distribution obligatoire

Un prototype jetable mais représentatif doit prouver :

1. AI Packs individuels sous 1,5 Go compressés et AAB sous le plafond cumulatif retenu ;
2. livraison on-demand dans une piste Play interne, y compris pause, annulation, reprise Wi-Fi/données mobiles et app sideload/install ;
3. disponibilité de chaque fragment après `COMPLETED` et absence de dépendance à un chemin de pack mis en cache entre lancements ;
4. copie/réassemblage en streaming sans charger le modèle en RAM ;
5. espace disque temporaire suffisant, interruption/reprise et nettoyage des fragments ;
6. hash complet final identique au fichier de référence ;
7. chargement réel par la version épinglée de LiteRT-LM et génération de contrôle ;
8. désinstallation, mise à jour, rollback et suppression de pack ;
9. comportement hors ligne après le téléchargement ;
10. audit de l'App Bundle généré, du manifeste fusionné et des termes Play.

**Décision de repli :** si le sharding est impossible, non autorisé ou trop fragile, E2B et E4B sont importés par SAF. Le produit ne retarde pas la release et ne prétend pas fournir un bouton de téléchargement intégré.

## 15.4 Stratégie de release modèle

- APK/AAB de base : application et runtime, sans modèle multi-gigaoctet.
- Modèle par défaut candidat : E2B, distribué via AI Packs uniquement si le spike 15.3 passe ; sinon import SAF.
- E4B : import SAF dans la première stratégie de production. Une distribution Play séparée ou ciblée n'est étudiée qu'après vérification des limites AAB, du sharding et des appareils pris en charge.
- Ne pas inclure E2B et E4B complets dans le même AAB par défaut : leurs tailles de fichiers publiées totalisent environ 6,23 Go avant le packaging, ce qui rend la limite cumulative AI Pack de 4 Go très probablement bloquante. La décision finale repose sur la taille empaquetée et mesurée du bundle ; aucune release ne peut supposer que la compression résout l'écart. Les variantes ciblées par appareil ne sont autorisées qu'après preuve par `bundletool` et Play internal testing.
- Le téléchargement est explicitement initié par l'utilisateur. Le volume, l'espace temporaire requis, la licence, la compatibilité et la possibilité de désinstaller sont affichés. Aucun téléchargement silencieux de plusieurs Go.
- Pour les packs volumineux, demander une confirmation claire lorsque le service Play l'exige pour l'utilisation de données mobiles ; les API officielles signalent notamment des confirmations pour les téléchargements importants hors Wi-Fi. [R33]

## 15.5 Corpus Packs : canal séparé des AI Packs

Les AI Packs Play sont réservés aux modèles. Ils ne peuvent pas contenir les bases SQLite, le corpus, les licences, le code ou les bibliothèques du produit.

- Un petit corpus cœur peut être intégré dans la base de l'application si sa taille compressée respecte les limites Play, si ses droits le permettent et si son update cadence est acceptable.
- Les Corpus Packs additionnels sont des fichiers `.tpack` signés, distribués avec la release de l'application quand cela est raisonnable ou importés via SAF.
- La limite de 1,5 Go par AI Pack ne devient pas une limite de Corpus Pack : c'est notre format local qui contrôle la taille, le nombre de fichiers et l'espace de staging. Les limites du canal de distribution utilisé doivent être respectées séparément.
- Play Asset Delivery standard n'est pas retenu par défaut pour les corpus d'une application non-jeu ; il ne sera utilisé qu'après validation de son applicabilité et de ses conditions actuelles.
- Chaque import est explicite, avec manifeste, signature, licence, empreintes, taille, version, langue et contenu avant activation.

## 15.6 Hugging Face, GitHub Releases et import utilisateur

Hugging Face est un hébergeur tiers, pas un backend de notre produit. Pour les modèles LiteRT Community, l'application peut montrer une fiche d'import avec dépôt, commit exact, licence déclarée, nom du fichier et SHA-256 attendus. L'application n'intègre aucun downloader Hugging Face et ne transmet aucune donnée à Hugging Face. L'utilisateur télécharge avec le navigateur ou un ordinateur, puis choisit le fichier via SAF.

ADB `push` est réservé au développement, aux tests et au support technique ; ce n'est pas le parcours utilisateur standard.

GitHub Releases impose une limite de moins de 2 Gio par asset individuel. Des fragments pourraient techniquement contourner cette limite, mais cela n'en fait pas un mécanisme de téléchargement intégré ni une source fiable de distribution de plusieurs Go à long terme. GitHub Actions ne sert jamais de stockage de poids de modèles. [R24][R25]

## 15.7 Parcours de première ouverture

1. Choix de la langue d'interface arabe/anglais et paramètres initiaux ; aucun madhhab n'est présélectionné.
2. Explication claire : l'application fonctionne sans modèle installé, avec fonctions déterministes et RAG selon composants disponibles.
3. Écran « Modèles locaux » : montre E2B/E4B, taille exacte du fichier variante, espace temporaire, compatibilité préflight et canal disponible (Play AI Pack ou import).
4. L'utilisateur déclenche explicitement le téléchargement Play ou choisit un fichier SAF.
5. Validation de l'empreinte, du format, de la licence, du runtime et du test de génération ; activation atomique seulement après succès.
6. Écran distinct « Corpus et sources » pour installer les packs documentaires et accepter leurs licences.
7. Le statut modèle/corpus est accessible dans les paramètres et les cartes montrent les limites dues à une absence de ressources.

## 15.8 Variantes de manifeste et contrôle réseau

`offlineSideload` : aucune permission `INTERNET` dans le manifeste final. `playDistribution` : si Play AI Delivery l'exige, cette permission peut être présente uniquement en raison du SDK officiel ; le code produit n'a aucune couche réseau applicative. La CI inspecte le manifeste fusionné et échoue si une permission réseau ou un SDK réseau non prévu est introduit. Le rapport de release énumère les dépendances qui ont ajouté les permissions.

Un build Play avec AI Delivery est soumis aux conditions Play et à la disponibilité du service ; la variante SAF est le fallback indépendant. L'absence de serveur applicatif reste absolue pour le produit.

## 15.9 GitHub Actions n'est pas le distributeur runtime

La CI compile, teste, scanne, génère SBOM, signe les builds de release dans un environnement protégé et archive des artefacts adaptés à sa politique de rétention. Les modèles multi-gigaoctets ne sont pas stockés dans les artefacts Actions. GitHub Releases peut héberger des manifestes, checksums et assets sous ses limites, mais n'est pas requis au fonctionnement de l'application. [R24][R25]

# 16. Dépendances et toolchain candidates

Les versions suivantes sont un snapshot de référence au 5 novembre 2026, pas un substitut aux tests de résolution Gradle. Toutes les dépendances sont verrouillées dans `gradle/libs.versions.toml`, avec dependency locking et verification metadata.

| Composant | Version candidate | Rôle |
|---|---:|---|
| Android Gradle Plugin | 9.3.1 | Build Android |
| Gradle Wrapper | 9.5.0 | Build reproductible |
| JDK | 17 | Runtime Gradle/AGP |
| Kotlin | 2.4.21 | Langage |
| Compose Compiler Gradle Plugin | 2.4.21 | Compose compiler |
| Compile SDK | 36 | Compilation |
| Target SDK | 36 | Exigence Google Play nouvelle app au 31 août 2026 |
| Compose BOM | 2026.09.00 | Alignement Compose |
| Navigation Compose | 2.10.1 | Navigation |
| Room | 2.8.5 | Persistance relationnelle |
| AndroidX SQLite | 2.7.1 | Abstraction SQLite |
| WorkManager | 2.12.0 | Jobs différables persistants |
| DataStore | 1.2.1 | Préférences non sensibles |
| LiteRT-LM | version exacte à épingler après spike | Exécution LLM |
| SQLCipher Android | version exacte à épingler après spike | Chiffrement DB |

Justification importante : Kotlin 2.4.21 indique une plage de compatibilité complète AGP allant jusqu'à 9.3.1. AGP 9.5.0 existe mais dépasse cette borne documentée ; il n'est donc pas retenu dans le snapshot malgré sa nouveauté. AGP 9.3 demande Gradle 9.5.0 et JDK 17. [R08][R09][R10]

Room 2.8.5, WorkManager 2.12.0, DataStore 1.2.1, Compose BOM 2026.09.00 et Navigation 2.10.1 sont les versions stables relevées dans les notes officielles au moment de la recherche. [R07][R26][R27][R28][R29]

## 16.1 Dépendances non figées

Les composants suivants nécessitent un spike avant adoption :

- KSP version pleinement compatible avec Kotlin 2.4.21 et AGP retenu ;
- LiteRT-LM exact artifact/version et règles R8/ABI ;
- SQLCipher Android exact artifact, intégration Room et migration ;
- moteur vectoriel HNSW ou calcul exact optimisé ;
- reranker multilingue arabe/anglais ;
- OCR arabe dédié ;
- ASR arabe pour récitation/commandes ;
- moteur de calcul de prière et jeux de conformité ;
- provider Ed25519 vérifié sur le `minSdk` retenu et intégration SQLCipher/Room exacte.

Aucune version `latest.release`, `+`, branche `main` ou snapshot n'est autorisée dans les builds de release.

# 17. GitHub Actions et chaîne d'approvisionnement

## 17.1 Workflows

### Pull request

- checkout par SHA complet ;
- setup JDK 17 ;
- validation Gradle Wrapper ;
- `./gradlew check lint testDebugUnitTest assembleDebug` ;
- vérification des dépendances et licences ;
- tests instrumentés sur émulateurs API 29/33/36 ;
- upload des rapports et APK debug de taille raisonnable.

### Nightly / main

- tests instrumentés complets ;
- tests de migrations Room ;
- tests de non-régression arabe/anglais/RTL ;
- tests de corpus fixtures ;
- scan des dépendances, secrets et licences ;
- génération SBOM ;
- analyse R8 et vérification shrinker ;
- archivage de rapports.

### Release tag

- exiger branche protégée et approbation d'environnement release ;
- revérifier licences et manifeste de corpus ;
- construire AAB/APK release ;
- tests de signature, hashes et métadonnées ;
- SBOM et notes de version ;
- signer uniquement dans un environnement protégé, sans exposer le keystore dans le dépôt ;
- publier les artefacts de release.

## 17.2 Sécurité de workflow

- `permissions: contents: read` par défaut ; ouvrir les permissions minimales par job.
- Contrôle automatisé du manifeste fusionné : la variante de production ne doit pas contenir `android.permission.INTERNET` ni une permission réseau introduite par une dépendance transitive. Toute exception exige un ADR et une nouvelle décision explicite du propriétaire du produit.
- Pin des actions GitHub/tiers par SHA complet.
- Environnements protégés pour signature/publication.
- Secrets masqués, jamais imprimés ni intégrés dans les APK de debug.
- Dépendances Gradle verrouillées et vérifiées par checksum.
- SBOM CycloneDX ou SPDX et inventaire de licences.
- Dépendabot/renovate ou mécanisme équivalent pour proposer les mises à jour, avec CI obligatoire.
- Pas de téléchargement de données utilisateur dans la CI.

## 17.3 Limite de ce que la CI valide

Un émulateur ne prouve pas la performance GPU/NPU d'un téléphone, la consommation thermique ou la qualité audio. Les tests réels doivent être effectués sur une matrice d'appareils physiques et consignés dans des rapports de release.

# 18. Stratégie de tests et critères d'acceptation production

## 18.1 Tests unitaires et propriété

- règles de permissions ;
- transitions d'états ;
- calculs de prière ;
- calculs de Zakat ;
- normalisation arabe ;
- fusion RRF ;
- conversion et formatage de dates/fuseaux ;
- idempotence et rollback ;
- parseurs de manifeste et vérificateurs de signatures.

Les tests property-based couvrent limites, valeurs négatives interdites, dates de changement d'heure, et données incomplètes.

## 18.2 Tests d'intégration

- import d'un Corpus Pack correct ;
- rejet de signature incorrecte ;
- rejet de hash incorrect ;
- rejet de chemin ZIP malveillant ;
- rejet de licence absente ;
- migration de base ;
- index lexical et vectoriel cohérents ;
- reprise de tâche après kill ;
- approbation après redémarrage ;
- revalidation des paramètres avant exécution.

## 18.3 Tests de RAG

Dataset de référence constitué par des spécialistes avec questions arabes et anglaises : recherche exacte, reformulation, translittération, variantes orthographiques, désaccord entre écoles, hadith avec classifications divergentes, question insuffisamment spécifiée et question hors corpus.

Mesures : Recall@K, Precision@K, MRR, NDCG, citation-resolution rate, exact quote match, coverage des divergences et abstention correcte.

Critère bloquant : zéro citation fabriquée dans le jeu de tests de release ; chaque citation visible doit résoudre à un passage exact et à une version d'édition connue. Les seuils retrieval seront fixés après création du gold set ; aucune cible arbitraire ne remplace l'évaluation savante.

## 18.3.1 Tests du reranking adaptatif et du mode dégradé

- référence exacte : le reranker est sauté lorsque la résolution exacte est univoque ;
- requête arabe simple : comparer FTS, dense, RRF et reranking ;
- requête bilingue / translittérée : vérifier la récupération croisée ;
- faible marge entre candidats : le policy engine déclenche le reranker uniquement si disponible ;
- modèle reranker absent, erreur native ou pression thermique : RRF-only ;
- domaine à enjeu élevé et evidence insuffisante après reranking : abstention ;
- vérification que le fallback ne retire jamais les filtres de provenance/licence ni les limites d'école ;
- contrôle des seuils sur jeu de validation séparé du jeu de test final.

Critère : mesurer les gains sur Recall@K, MRR/NDCG, exact-match des citations, taux d'abstention correcte, RAM/PSS, latence P95, énergie et température. Aucun reranker n'est accepté sur la seule base de benchmarks anglophones génériques.

## 18.4 Tests d'autorisation

Cas obligatoires :

- tentative d'exécution sans approbation ;
- refus puis redémarrage ;
- approbation expirée ;
- grant révoqué ;
- grant de type proche mais non identique ;
- paramètres changés après approbation ;
- cible changée après approbation ;
- double callback ;
- action partiellement exécutée ;
- changement concurrent de ressource ;
- tentative de tool call automatique du runtime.

Critère bloquant : zéro mutation non autorisée dans les tests de sécurité. La configuration d'intégration doit prouver que `automaticToolCalling=false` est maintenu.

## 18.4.1 Tests de persistance, checkpoint et outbox

Injecter un kill du processus et un redémarrage système après chaque transition pertinente : tâche créée, lease acquis, étape calculée, checkpoint écrit, carte publiée, proposition en attente, approbation écrite, action préparée, action appelée mais reçu non persisté, action partiellement appliquée et Canvas modifié.

Vérifier :

- reprise exacte du dernier checkpoint compatible ;
- absence de deux leases valides sur la même étape ;
- `fencingToken` empêche un ancien worker d'écrire après reprise ;
- aucun état `WAITING_FOR_APPROVAL` n'est exécuté au démarrage ;
- approbation invalide si proposal hash, cible, paramètre, policyVersion ou précondition a changé ;
- outbox réconcilie une action ambiguë sans la rejouer aveuglément ;
- aucune double écriture Calendar Provider lors de relance ;
- Canvas restauré avec étapes, hypothèses, preuves et versions exactes ;
- migration de `stateSchemaVersion` testée avec snapshots de versions antérieures ;
- tâche incompatible avec une nouvelle version de corpus/modèle présentée en lecture seule ou recalculée dans un nouveau snapshot, jamais écrasée.

## 18.5 Tests matériels

Matrice minimale :

- appareil de référence Samsung Galaxy S24 FE ;
- au moins un appareil Google Pixel Tensor ;
- au moins un appareil Qualcomm ;
- appareil d'entrée de gamme pris en charge ;
- Android versions API 26/29, 33, 36 selon minSdk décidé ;
- ABI arm64-v8a obligatoire et x86_64 pour émulateur.

Mesures : RAM P50/P95, chargement du modèle, TTFT, tokens/s, batterie, température, latence retrieval, taille disque, durée d'import et crash rate. Les fonctions IA sont désactivées ou dégradées lorsque le runtime signale une pression mémoire/thermique.

## 18.5.1 Tests d'installation, distribution et capacité mémoire

- Tester la variante Play sur une piste interne, avec état `PENDING`, `DOWNLOADING`, `WAITING_FOR_WIFI`, `REQUIRES_USER_CONFIRMATION`, `COMPLETED`, `FAILED` et `CANCELED`.
- Tester import SAF d'un seul fichier de plusieurs Go, fichier tronqué, mauvais MIME, provider lent, URI révoquée, espace disque faible et interruption.
- Si modèle sharding : reconstruire et comparer le hash final, tuer l'app pendant copie, reprendre, supprimer fragments après succès et ouvrir le modèle final.
- Mesurer temps, stockage temporaire, espace final, RAM/PSS, backend réel, TTFT, tokens/s, chauffe et batterie sur S24 FE et autres appareils de référence.
- Tester une situation de `lowMemory`, callbacks de trim, thermique élevée, manque de stockage et backend GPU/NPU indisponible.
- Vérifier qu'un échec de preflight n'empêche pas l'accès à la Timeline, aux calculateurs locaux et au RAG restant.
- Vérifier l'absence d'Internet en build offline et l'absence d'appel HTTP applicatif en build Play.

## 18.6 Performance : budgets initiaux à confirmer

| Opération | Objectif de conception initial |
|---|---:|
| Affichage timeline depuis base locale | P95 < 500 ms après écran prêt |
| Recherche exacte par référence | P95 < 100 ms |
| Retrieval hybride sans génération | P95 < 1,5 s sur appareil de référence |
| Reprise d'une tâche existante | P95 < 1 s hors chargement modèle |
| Chargement initial modèle | Mesuré et présenté ; jamais caché comme un délai UI |

Ces nombres sont des objectifs d'ingénierie, pas des performances déjà démontrées. Ils sont modifiables après benchmark, mais tout changement doit être documenté.

# 19. Critères de publication

Une release publique n'est autorisée que si les portes suivantes sont toutes vertes :

| Porte | Preuve requise |
|---|---|
| G1 — Sources et licences | Dossier de provenance et droits approuvé pour chaque pack |
| G2 — Revue sunnite | Corpus, avis et règles relus par spécialistes qualifiés pour chaque domaine activé |
| G3 — RAG | Gold set, métriques, zéro citation inventée, abstention validée |
| G4 — Autorisation | Suite tests prouve zéro action modificatrice non autorisée |
| G5 — Sécurité | Audit MASVS, sauvegarde, clés, logs et dépendances |
| G6 — Android | Tests API et appareils, RTL, accessibilité, cycle de vie |
| G7 — Modèles | Modèles/licences/versions/benchmarks publiés et limites indiquées |
| G8 — Opérations | Procédure de correction, révocation d'un pack et rollback |
| G9 — Distribution | AAB/APK, SBOM, signatures et checksums vérifiés |
| G10 — Modèle local | Import/Play delivery, hash, preflight, mémoire, restauration et suppression démontrés sur appareils cibles |
| G11 — Reprise agentique | Tests crash-injection, checkpoint, lease, outbox et Canvas restaurés sans mutation non autorisée |
| G12 — Distribution Play | Si AI Packs activés : piste Play interne, limites de taille, statut, sharding (si utilisé), manifeste et fallback SAF démontrés ; sinon l'option Play AI est désactivée |

Un module ne peut pas afficher un statut « vérifié » tant que son dossier documentaire et ses critères de validation ne sont pas achevés. Le produit peut rester complet dans sa conception même si une fonction réglementée ou religieusement complexe n'est pas activée dans une release tant que ses portes ne sont pas satisfaites.

# 20. Risques et décisions techniques ouvertes

| Risque | Impact | Réponse |
|---|---|---|
| Droits des corpus agrégés incertains | Blocage commercial | Audit licence par source et dérivé |
| Qualité arabe embeddings insuffisante | RAG erroné | Gold set arabe, comparaison modèles et tokenizers |
| Modèles de plusieurs Go | Installation et stockage difficiles | Import local, gestionnaire modèles, stockage requis visible |
| Limite Play AI Pack de 1,5 Go / AAB 4 Go | Modèle non livrable tel quel | Sharding à prouver ; sinon import SAF ; pas de promesse avant le spike |
| AI Delivery bêta | API/conditions susceptibles d'évoluer | Adapter isolé, version épinglée, fallback SAF permanent |
| E4B charge mémoire variable | OOM, chauffe, kill système | Profil matériel + preflight runtime ; aucun seuil 7 Go universel |
| Crash après effet de bord | Action répétée ou état divergent | Outbox et reconciliation ; `OUTCOME_UNKNOWN` sans rejeu automatique |
| Sources sans fraîcheur live | Carte trompeuse | Labels source/âge/stale et aucun libellé « live » |
| E4B trop coûteux | Chauffe, RAM et latence | Profil appareil, E2B, chargement à la demande |
| OCR coranique imparfait | Mauvaise référence de verset | Rapprochement avec corpus exact et confirmation utilisateur |
| Hadith mal classé | Mauvaise explication religieuse | Classifications attribuées, pas de label global non sourcé |
| Divergence mal représentée | Biais d'école | Corpus par école, revue et affichage des avis distincts |
| Aucun serveur | Données live indisponibles | Import local et datation des données |
| Interruption Android | Action doublée/perdue | Persistance, idempotence et reprise |
| Tool calling automatique | Action non autorisée | Désactivation obligatoire + execution gate indépendant |
| Dépendance native | Crash/ABI incompatibles | Matrice appareils et tests release |
| Journaux trop détaillés | Fuite de données privées | Minimisation, chiffrement, redaction |

## 20.1 Décisions à prendre pendant les spikes, pas à la place du propriétaire du produit

1. `minSdk` définitif : API 24 est le plancher technique des versions candidates LiteRT-LM/WorkManager, mais le plancher commercial doit être choisi selon la matrice de téléphones et les exigences de sécurité.
2. Moteur vectoriel : exact quantifié ou HNSW après benchmark.
3. Re-ranker : sélection uniquement si le gain arabe/anglais compense mémoire, latence, énergie et licence.
4. Modèle OCR arabe et ASR : aucune sélection définitive sans benchmark et audit de licence.
5. Liste définitive des ouvrages, éditions, traductions et autorités pour les quatre écoles : validation par comité savant.
6. Politique de rétention des documents importés et durée de conservation de l'audit : réglage produit et juridique.
7. Distribution Play AI Packs : verdict du spike de sharding/reconstruction, de la limite AAB et des conditions bêta.
8. Variante réseau Play : confirmer les permissions fusionnées et s'assurer que l'application n'expose aucun client HTTP applicatif.
9. Profil mémoire E4B : seuils par appareil dérivés des mesures PSS/thermal/runtime, non d'un chiffre marketing RAM.
10. Version exacte de l'API Play AI Delivery et stratégie de remplacement si la bêta change ou disparaît.

# 21. ADR — décisions formalisées

| ADR | Décision | Statut |
|---|---|---|
| ADR-001 | Application Android native Kotlin/Compose | Acceptée |
| ADR-002 | Local-only, aucun backend et pas de réseau app par défaut | Acceptée |
| ADR-003 | Quatre écoles sunnites représentées séparément | Acceptée |
| ADR-004 | Corpus hybrid core + collections complémentaires | Acceptée |
| ADR-005 | Room comme persistance des états utilisateur | Acceptée sous validation encryption adapter |
| ADR-006 | FTS5/BM25 + dense + RRF | Acceptée |
| ADR-007 | Vector backend abstrait, HNSW non imposé sans benchmark | Acceptée |
| ADR-008 | EmbeddingGemma 2 Text comme candidat, pas modèle figé | À benchmarker |
| ADR-009 | LiteRT-LM Kotlin pour Gemma, tool calling manuel | Acceptée sous spike d'intégration |
| ADR-010 | Approval Engine indépendant du LLM | Acceptée |
| ADR-011 | Toute mutation nécessite une permission explicite | Acceptée |
| ADR-012 | Actions sensibles toujours confirmées individuellement | Acceptée |
| ADR-013 | Packs signés, versionnés et importés localement | Acceptée |
| ADR-014 | Aucune transmission de données privées et aucune analytics distante | Acceptée |
| ADR-015 | Validation savante et audit licence comme portes de release | Acceptée |
| ADR-016 | Play AI Packs on-demand évalué pour E2B ; sharding multi-pack uniquement après preuve ; SAF fallback obligatoire | Conditionnelle / spike bloquant |
| ADR-017 | E4B import SAF par défaut tant que le sharding/quotas Play ne sont pas prouvés | Acceptée sous réserve |
| ADR-018 | Corpus Packs via bundle léger autorisé ou SAF ; AI Packs réservés aux modèles | Acceptée |
| ADR-019 | Reranking conditionnel ; RRF-only dégradé avec abstention renforcée | Acceptée |
| ADR-020 | Room checkpoints + leases + event log + outbox ; WorkManager n'est pas la source de vérité | Acceptée |
| ADR-021 | E4B évalué par preflight/runtime et profils appareil ; aucun seuil RAM universel | Acceptée |
| ADR-022 | Données de vol/communauté/import affichent provenance et fraîcheur | Acceptée |
| ADR-023 | Variante offline sans INTERNET ; variante Play autorise uniquement le réseau requis par Play AI Delivery si activé | Conditionnelle |

# 22. Plan d'ingénierie de production

Ce programme n'est pas un MVP. Il organise le développement du produit complet par tranches verticales vérifiables.

## Lot A — Fondations et gouvernance

- repository, CI, conventions, ADR, licences ;
- module de sécurité, base utilisateur chiffrée ;
- modèle de domaine, migrations et tests de reprise ;
- catalogue de sources et processus de validation savante.

## Lot B — Plateforme documentaire

- ingestion ;
- schéma documentaire ;
- packs signés ;
- import/rollback ;
- reader et citations ;
- retrieval lexique/dense et banc d'essai.

## Lot C — Plateforme agentique

- modèle Task ;
- orchestrateur persistant ;
- tool registry ;
- moteur d'autorisation ;
- carte de décision ;
- reprise après kill et tests d'idempotence.

## Lot D — Moteurs métier

- prière ;
- Hifz ;
- voyage ;
- Halal ;
- Zakat ;
- héritage ;
- répertoire communautaire local.

Chaque module est livré avec ses jeux d'essai, ses sources et sa revue spécialisée.

## Lot E — Modèles et multimodalité

- Model Manager ;
- LiteRT-LM E2B/E4B ;
- EmbeddingGemma 2 Text ;
- OCR arabe ;
- ASR ;
- reranker si retenu ;
- profils de performance et dégradation.

## Lot F — Produit complet et release

- timeline adaptative ;
- canvas ;
- RTL et anglais ;
- accessibilité ;
- tests de sécurité ;
- tests matériels ;
- dossiers de publication ;
- release signée.

# 23. Références techniques consultées

Les versions et pages ci-dessous ont été consultées pour établir le snapshot vérifié le 5 novembre 2026. Vérifier à nouveau les notes officielles au moment de figer les dépendances d'implémentation.

| Réf. | Sujet | Source |
|---|---|---|
| R01 | Architecture Android | https://developer.android.com/topic/architecture |
| R02 | Guide de modularisation Android | https://developer.android.com/topic/modularization |
| R03 | Gemma 4 et LiteRT-LM | https://developers.google.com/edge/litert-lm/models/gemma-4 |
| R04 | Aperçu LiteRT-LM et benchmarks | https://developers.google.com/edge/litert-lm/overview |
| R05 | EmbeddingGemma 2 Model Card | https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2 |
| R06 | API Kotlin LiteRT-LM et tool calling manuel | https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md |
| R07 | WorkManager release notes | https://developer.android.com/jetpack/androidx/releases/work |
| R08 | AGP 9.3 release notes | https://developer.android.com/build/releases/agp-9-3-0-release-notes |
| R09 | AGP releases et compatibilité | https://developer.android.com/build/releases/about-agp |
| R10 | Compatibilité Kotlin Gradle Plugin | https://kotlinlang.org/docs/gradle-configure-project.html |
| R11 | Kotlin releases | https://kotlinlang.org/docs/releases.html |
| R12 | Licence texte Tanzil | https://tanzil.net/docs/Text_License |
| R13 | Licence OpenITI | https://openiti.org/documentation/ |
| R14 | Conditions et reproduction Sunnah.com | https://sunnah.com/about |
| R15 | Fiche dataset IslamicRAG-7M | https://huggingface.co/datasets/maddi99/islamicrag-7m |
| R16 | SQLite FTS5 et tokenizer | https://www.sqlite.org/fts5.html |
| R17 | SQLite AndroidX et sqlite-bundled | https://developer.android.com/jetpack/androidx/releases/sqlite |
| R18 | Alarmes exactes Android | https://developer.android.com/develop/background-work/services/alarms |
| R19 | SQLCipher Android | https://github.com/sqlcipher/sqlcipher-android |
| R20 | Android Security Crypto / Keystore | https://developer.android.com/jetpack/androidx/releases/security et https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec |
| R21 | Backup Android | https://developer.android.com/guide/topics/manifest/application-element |
| R22 | OWASP MASVS | https://github.com/OWASP/masvs/releases |
| R23 | Langues, RTL et bidirectionnalité | https://developer.android.com/training/basics/supporting-devices/languages |
| R24 | GitHub Actions limits | https://docs.github.com/en/actions/reference/limits |
| R25 | GitHub Releases asset limits | https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases |
| R26 | Compose BOM setup | https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler |
| R27 | Room release notes | https://developer.android.com/jetpack/androidx/releases/room |
| R28 | DataStore release notes | https://developer.android.com/jetpack/androidx/releases/datastore |
| R29 | Navigation release notes | https://developer.android.com/jetpack/androidx/releases/navigation |
| R30 | Google Play target API requirements | https://support.google.com/googleplay/android-developer/answer/11926878 |
| R31 | sqlite-vec — implémentation et limites de recherche vectorielle | https://github.com/asg017/sqlite-vec |
| R32 | Tink — notes de compatibilité des providers cryptographiques Android | https://developers.google.com/tink/known-issues |
| R33 | Play for On-device AI (bêta), limites, AI Packs, livraison et état | https://developer.android.com/google/play/on-device-ai |
| R34 | Limites générales de taille Google Play (à ne pas confondre avec les règles spécifiques AI Packs) | https://support.google.com/googleplay/android-developer/answer/9859372 |
| R35 | Gemma 4 LiteRT-LM et mesures E2B/E4B publiées par Google | https://developers.google.com/edge/litert-lm/models/gemma-4 |
| R36 | Model card LiteRT Community Gemma 4 E4B, version commitée et licence déclarée | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm |
| R37 | WorkManager — persistance et replanification après redémarrage | https://developer.android.com/develop/background-work/background-tasks/persistent |
| R38 | ActivityManager.MemoryInfo — mémoire totale, disponible, advertisedMem et lowMemory | https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo |
| R39 | LiteRT-LM Kotlin — tool calling manuel (`automaticToolCalling = false`) | https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md |
| R40 | sqlite-vec — README officiel et capacités documentées | https://github.com/asg017/sqlite-vec |
| R41 | GitHub Releases — limite de moins de 2 Gio par asset individuel | https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases |


---

**Fin du cahier d'architecture — version 1.2.**

Cette version 1.2 remplace les versions 1.0 et 1.1 comme base de référence pour les décisions d'implémentation. Toute évolution qui change la politique d'approbation, le périmètre religieux, les droits de corpus, l'absence de serveur ou les frontières entre modules doit créer ou mettre à jour un ADR.
