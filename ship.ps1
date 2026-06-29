# Set execution policy for the session
$ErrorActionPreference = "Continue"

Write-Host "=== Préparation de la release pour l'envoi sur AWS ===" -ForegroundColor Green

# 1. Read instance IP
if (-not (Test-Path "..\instance_ip.txt")) {
    Write-Error "Fichier instance_ip.txt introuvable. Veuillez d'abord exécuter deploy.ps1."
    exit 1
}
$ip = Get-Content "..\instance_ip.txt" -Raw
$ip = $ip.Trim()
Write-Host "Adresse IP cible du serveur : $ip" -ForegroundColor Cyan

$keyFile = "..\grh-deploy-key.pem"

# 2. Create release directory structure
Write-Host "Création de la structure temporaire de release..." -ForegroundColor Cyan
$releaseDir = "release"
if (Test-Path $releaseDir) { Remove-Item $releaseDir -Recurse -Force }
New-Item -ItemType Directory -Path $releaseDir | Out-Null
New-Item -ItemType Directory -Path "${releaseDir}\backend" | Out-Null
New-Item -ItemType Directory -Path "${releaseDir}\frontend" | Out-Null

# Copy backend files
Copy-Item "docker-compose.prod.yml" -Destination "${releaseDir}\docker-compose.prod.yml"
Copy-Item "backend\Dockerfile.prod" -Destination "${releaseDir}\backend\Dockerfile.prod"
Copy-Item "backend\target\grh-backend-0.0.1-SNAPSHOT.jar" -Destination "${releaseDir}\backend\grh-backend-0.0.1-SNAPSHOT.jar"

# Copy frontend files
Copy-Item "frontend\Dockerfile.prod" -Destination "${releaseDir}\frontend\Dockerfile.prod"
Copy-Item "frontend\nginx.conf" -Destination "${releaseDir}\frontend\nginx.conf"
Copy-Item -Recurse "frontend\dist\frontend\browser" -Destination "${releaseDir}\frontend\browser"

# 3. Compress release folder using native tar
Write-Host "Empaquetage de l'archive release.tar.gz..." -ForegroundColor Cyan
if (Test-Path "..\release.tar.gz") { Remove-Item "..\release.tar.gz" -Force }
tar -czf "..\release.tar.gz" -C $releaseDir .

# Clean up local release directory
Remove-Item $releaseDir -Recurse -Force

# 4. Wait for SSH / Docker to be ready on EC2 instance (polling)
Write-Host "Attente de l'installation de Docker sur le serveur EC2 (cela prend généralement 1-2 minutes)..." -ForegroundColor Cyan
$maxAttempts = 30
$attempt = 1
$dockerReady = $false

while (-not $dockerReady -and $attempt -le $maxAttempts) {
    Write-Host "Vérification de l'état de Docker (Tentative $attempt/$maxAttempts)..." -ForegroundColor Gray
    # Run a test ssh command to check docker version
    $output = & ssh -i $keyFile -o StrictHostKeyChecking=no ubuntu@${ip} "docker --version" 2>$null
    if ($output -like "*Docker version*") {
        $dockerReady = $true
    } else {
        Start-Sleep -Seconds 10
        $attempt++
    }
}

if (-not $dockerReady) {
    Write-Error "Docker n'est pas prêt sur le serveur après 5 minutes. Abandon."
    exit 1
}

Write-Host "Docker est installé et opérationnel sur le serveur !" -ForegroundColor Green

# 5. Transfer archive via SCP
Write-Host "Copie de l'archive release.tar.gz vers le serveur EC2..." -ForegroundColor Cyan
scp -i $keyFile -o StrictHostKeyChecking=no "..\release.tar.gz" "ubuntu@${ip}:/home/ubuntu/"

# 6. Extract and run on the remote EC2 instance
Write-Host "Décompression de la release et lancement de l'application sur le serveur EC2..." -ForegroundColor Cyan
$remoteCommand = "mkdir -p grh-app && tar -xzf release.tar.gz -C grh-app && cd grh-app && " +
                 "echo 'POSTGRES_DB=grh_db' > .env && " +
                 "echo 'POSTGRES_USER=postgres' >> .env && " +
                 "echo 'POSTGRES_PASSWORD=password' >> .env && " +
                 "echo 'S3_ACCESS_KEY=admin' >> .env && " +
                 "echo 'S3_SECRET_KEY=supersecretpassword' >> .env && " +
                 "echo 'S3_BUCKET=grh-pictures' >> .env && " +
                 "echo 'JWT_SECRET=Gj5pX92sKz8wV1a4m7d8f9g0hJkLmNpQrStUvWxYz1234567890abcdefghijklmnopqrstuv' >> .env && " +
                 "docker compose -f docker-compose.prod.yml up -d --build"

ssh -i $keyFile -o StrictHostKeyChecking=no ubuntu@${ip} $remoteCommand

Write-Host "=== Déploiement terminé avec succès ! ===" -ForegroundColor Green
Write-Host "Accédez à l'application sur : http://${ip}" -ForegroundColor Green
