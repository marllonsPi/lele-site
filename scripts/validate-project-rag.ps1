$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$requiredFiles = @(
  "AGENTS.md",
  "index.html",
  "styles.css",
  "CNAME",
  "project-rag/README.md",
  "project-rag/memory/current-state.md",
  "project-rag/memory/handoff.md",
  "project-rag/context/architecture.md",
  "project-rag/context/product.md"
)

foreach ($relativePath in $requiredFiles) {
  $fullPath = Join-Path $projectRoot $relativePath
  if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
    throw "Arquivo obrigatorio ausente: $relativePath"
  }
}

$forbiddenPatterns = @(
  "SUPABASE_SERVICE_ROLE_KEY=",
  "sb_secret_",
  "-----BEGIN PRIVATE KEY-----"
)

$ragFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot "project-rag") -Recurse -File
foreach ($file in $ragFiles) {
  $content = Get-Content -Raw -LiteralPath $file.FullName
  foreach ($pattern in $forbiddenPatterns) {
    if ($content.Contains($pattern)) {
      throw "Padrao sensivel encontrado em $($file.FullName): $pattern"
    }
  }
}

Write-Output "RAG local validada: $($requiredFiles.Count) arquivos obrigatorios."
