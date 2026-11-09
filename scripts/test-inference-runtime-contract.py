#!/usr/bin/env python3
"""Static Phase 5B guards for pinned LiteRT-LM, CPU-only inference and offline boundaries."""
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
checks = 0

def require(condition: bool, label: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(label)

catalog_toml = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
app_gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
feature_gradle = (ROOT / "feature/model-management/build.gradle.kts").read_text(encoding="utf-8")
runtime_gradle = (ROOT / "core/inference-runtime/build.gradle.kts").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
generation = (ROOT / "core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/LiteRtCpuGenerationRuntime.kt").read_text(encoding="utf-8")
embedding = (ROOT / "core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/LiteRtEmbeddingTextModel.kt").read_text(encoding="utf-8")
contracts = (ROOT / "core/inference-runtime/src/main/java/app/timeline/islamic/core/inference/GenerationRuntimeContracts.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/app/timeline/islamic/TimelineApplication.kt").read_text(encoding="utf-8")
feature = (ROOT / "feature/model-management/src/main/java/app/timeline/islamic/feature/models/ModelManagementFeature.kt").read_text(encoding="utf-8")

versions = catalog_toml.get("versions", {})
libraries = catalog_toml.get("libraries", {})
require(versions.get("litertlm") == "0.19.1", "LiteRT-LM is pinned to exact version 0.19.1")
require(libraries.get("litertlm-android", {}).get("module") == "com.google.ai.edge.litertlm:litertlm-android" and
        libraries.get("litertlm-android", {}).get("version", {}).get("ref") == "litertlm",
        "version catalog resolves only the pinned Android LiteRT-LM Maven artifact")
require('include(":core:inference-runtime")' in settings, "the isolated inference runtime module is included")
require('implementation(project(":core:inference-runtime"))' in app_gradle and
        'implementation(project(":core:inference-runtime"))' in feature_gradle,
        "the application and Models screen use the isolated runtime boundary")
require('implementation(libs.litertlm.android)' in runtime_gradle and
        'implementation(libs.litertlm.android)' not in app_gradle,
        "LiteRT-LM native runtime dependency is isolated to core:inference-runtime")
require("sourceCompatibility = JavaVersion.VERSION_17" in runtime_gradle and
        "targetCompatibility = JavaVersion.VERSION_17" in runtime_gradle and
        re.search(r"test -n \"\$JAVA_HOME_21_X64\"", workflow) is not None,
        "Gradle runs with JDK 21 while Android module bytecode remains Java 17")
require("Backend.CPU(threadCount = CPU_THREAD_COUNT)" in generation and
        "Backend.GPU" not in generation and "Backend.NPU" not in generation,
        "text generation is CPU-only with a bounded thread count")
require("maxNumTokens = MAX_CONTEXT_TOKENS" in generation and "MAX_CONTEXT_TOKENS = 4_096" in generation,
        "generation KV/context cache is explicitly bounded")
require("tools = emptyList()" in generation and "automaticToolCalling = false" in generation and
        "message.toolCalls.isEmpty()" in generation,
        "generation cannot register or automatically execute tools and fails closed on tool-call output")
require("sendMessageAsync(safePrompt, maxOutputToken = outputLimit)" in generation and
        "MAX_OUTPUT_TOKENS = 256" in contracts and "MAX_RESPONSE_CHARACTERS = 12_000" in generation,
        "generation uses bounded streaming and validates output length")
require("Executors.newSingleThreadExecutor" in generation and "asCoroutineDispatcher()" in generation and
        "withContext(ownerDispatcher" in generation and "conversation.close()" in generation and
        "oldEngine.close()" in generation,
        "native generation engine/conversation lifecycle is single-owner and explicitly closed")
require("currentCoroutineContext().ensureActive()" in generation and "conversation.cancelProcess()" in generation,
        "generation initialization and streaming honour coroutine cancellation")
require("initializedCandidate = it" in generation and "candidate.isInitialized()" in generation and
        "Retain the reference until close succeeds" in generation,
        "generation initialization cancellation cannot orphan a completed native engine and close remains retryable")
require("Dispatchers.IO + NonCancellable" in embedding and "callerContext.ensureActive()" in embedding and
        "engine.close()" in embedding,
        "embedding initialization cancellation closes a native engine before returning cancellation")
require("private val modelManager: ModelManager" in generation and "modelManager.resolveInstalledFile(modelId)" in generation,
        "generation resolves an installed artifact through the Model Manager verification boundary")
require("EMBEDDING_DIMENSION = 768" in embedding and "outputSize = EMBEDDING_DIMENSION" in embedding,
        "EmbeddingGemma outputs exactly the 768-dimensional Phase 4 dense-index contract")
require('DOCUMENT_PREFIX = "task: search result | text: "' in embedding and
        'QUERY_PREFIX = "task: search query | text: "' in embedding,
        "embedding index/query use LiteRT-LM documented asymmetric retrieval prefixes")
require("computeEmbeddingBatchAsync" in embedding and "MAX_BATCH_SIZE = 24" in embedding and
        "MAX_INPUT_TOKENS = 8_192" in embedding and "MAX_INPUT_CHARACTERS = MAX_INPUT_TOKENS - maxOf(DOCUMENT_PREFIX.length, QUERY_PREFIX.length)" in embedding,
        "embedding calls use bounded async batches and bounded input length")
require("vector.all { it.isFinite() }" in embedding and "norm in 0.90..1.10" in embedding,
        "embedding adapter rejects wrong, non-finite and unnormalized vectors")
require("generationRuntime.unload()" in app and "LiteRtEmbeddingTextModel.open" in app and
        "inferenceLifecycleMutex" in app,
        "app serializes generation and embedding to avoid co-resident large model weights")
require("buildLocalEmbeddingIndexWithInstalledModel" in app and "embedSearchQueryWithInstalledModel" in app,
        "app exposes explicit installed-model paths for indexing and query embeddings")
require("fts" not in app.lower() or "LocalEmbeddingIndexBuilder" in app,
        "existing lexical retrieval/dense-index builder remains the integration boundary")
require("models_runtime_load_cpu" in feature and "models_runtime_test" in feature and
        "models_runtime_unload" in feature,
        "Models UI exposes explicit CPU load, smoke test and unload actions")

# Offline flavors and all manifests remain free of application network permission.
manifest_files = list((ROOT / "app/src").rglob("AndroidManifest.xml"))
for manifest in manifest_files:
    try:
        parsed = ET.parse(manifest).getroot()
        android_ns = "{http://schemas.android.com/apk/res/android}"
        forbidden = [node for node in parsed.findall("uses-permission")
                     if node.attrib.get(android_ns + "name") == "android.permission.INTERNET"]
        require(not forbidden, f"{manifest.relative_to(ROOT)} does not request INTERNET")
    except ET.ParseError as exc:
        require(False, f"{manifest.relative_to(ROOT)} parses: {exc}")

for label, needle in [
    ("unit test", ":core:inference-runtime:testDebugUnitTest"),
    ("lint", ":core:inference-runtime:lint"),
    ("API 36 instrumentation", ":core:inference-runtime:connectedDebugAndroidTest"),
]:
    require(needle in workflow, f"CI includes inference-runtime {label}")
require("scripts/test-inference-runtime-contract.py" in workflow,
        "CI executes the Phase 5B static source contract")
require('versionCode = 25' in app_gradle and
        'versionName = "0.7.0-phase6-domain-engines"' in app_gradle,
        "application release identity is 0.7.0 versionCode 25")

unit_tests = ROOT / "core/inference-runtime/src/test/java/app/timeline/islamic/core/inference/InferenceRuntimePolicyTest.kt"
android_tests = ROOT / "core/inference-runtime/src/androidTest/java/app/timeline/islamic/core/inference/LiteRtApiContractTest.kt"
require(unit_tests.is_file() and len(re.findall(r"@Test", unit_tests.read_text(encoding="utf-8"))) >= 10,
        "inference generation/embedding policies have focused JVM unit tests")
require(android_tests.is_file() and len(re.findall(r"@Test", android_tests.read_text(encoding="utf-8"))) >= 3,
        "API 36 instrumentation compiles and exercises the exact LiteRT-LM public configuration API")

if errors:
    print("Phase 5B inference-runtime contract FAILED:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)
print(f"All {checks} Phase 5B LiteRT-LM source, offline, lifecycle and CI contract checks passed.")
