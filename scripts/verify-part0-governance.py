#!/usr/bin/env python3
"""Static completeness guard for Part 0 governance records; not a release approval."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
required = {
    "docs/governance/README.md": ["Verified", "Pending", "NO-GO", "SOURCE_AND_RIGHTS_REGISTER.csv"],
    "docs/governance/SOURCE_AND_RIGHTS_REGISTER.csv": ["license_or_rights_basis", "artifact_sha256", "offline_storage_allowed"],
    "docs/governance/SCHOLARLY_REVIEW_RECORD_TEMPLATE.md": ["Madhhab scope", "Artifact SHA-256", "Reviewer sign-off"],
    "docs/governance/RELEASE_GATES.md": ["offline flavor", "Room schema", "Scholarly validation", "NO-GO"],
    "docs/governance/THREAT_MODEL.md": ["Trust boundaries", "AES-GCM", "Residual risk"],
    "docs/governance/CHANGE_EVIDENCE_POLICY.md": ["PASS", "FAIL", "BLOCKED", "NOT RUN"],
}
for filename, markers in required.items():
    path = root / filename
    if not path.is_file():
        raise SystemExit(f"Missing governance record: {filename}")
    text = path.read_text(encoding="utf-8")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise SystemExit(f"{filename} is missing required markers: {missing}")
readme = (root / "README.md").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
workflow_checks = {
    "README links governance records": "docs/governance/README.md" in readme,
    "offline debug APK is uploaded": "app/build/outputs/apk/offlineSideload/debug/*.apk" in workflow and "Upload offline sideload debug validation APK" in workflow,
    "APK SHA-256 manifest is generated": "sha256sum ./*.apk > SHA256SUMS.txt" in workflow,
    "validation evidence is uploaded even after failures": "Upload Android validation evidence" in workflow and workflow.count("if: always()") >= 4,
    "pinned artifact action is used": "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02" in workflow,
    "raw unit and instrumentation results are retained": "**/build/test-results/**" in workflow and "**/build/outputs/androidTest-results/**" in workflow,
}
failed = [name for name, ok in workflow_checks.items() if not ok]
if failed:
    raise SystemExit("Part 0 workflow checks failed: " + ", ".join(failed))
print(f"Part 0 governance static checks passed ({len(required)} records, {len(workflow_checks)} README/CI contracts). This does not constitute legal, scholarly or release sign-off.")
