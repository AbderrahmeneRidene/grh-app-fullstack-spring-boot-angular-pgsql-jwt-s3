# Set execution policy for the session
$ErrorActionPreference = "Continue"

Write-Host "=== Lancement du Nettoyage et du Redéploiement ===" -ForegroundColor Green

# 1. Terminate old EC2 instance if it exists
if (Test-Path "..\instance_id.txt") {
    $oldInstanceId = Get-Content "..\instance_id.txt" -Raw
    $oldInstanceId = $oldInstanceId.Trim()
    if ($oldInstanceId -ne "") {
        Write-Host "Terminaison de l'ancienne instance $oldInstanceId..." -ForegroundColor Yellow
        aws ec2 terminate-instances --instance-ids $oldInstanceId | Out-Null
        # Wait a few seconds for termination to start
        Start-Sleep -Seconds 5
    }
    Remove-Item "..\instance_id.txt" -Force
}
if (Test-Path "..\instance_ip.txt") { Remove-Item "..\instance_ip.txt" -Force }

# 2. Key pair cleanup and generation
$keyName = "grh-deploy-key"
$keyFile = "..\grh-deploy-key.pem"

if (Test-Path $keyFile) {
    Write-Host "Suppression de la clé locale..." -ForegroundColor Yellow
    Remove-Item $keyFile -Force
}

$existingKey = aws ec2 describe-key-pairs --query "KeyPairs[?KeyName=='$keyName'].KeyName" --output text
if ($existingKey -and $existingKey -ne "") {
    Write-Host "Suppression de la clé existante dans AWS..." -ForegroundColor Yellow
    aws ec2 delete-key-pair --key-name $keyName | Out-Null
}

Write-Host "Génération de la nouvelle paire de clés SSH avec formatage brut..." -ForegroundColor Cyan
# Run via cmd.exe to ensure exact raw string output redirection with newlines preserved
cmd.exe /c "aws ec2 create-key-pair --key-name $keyName --query KeyMaterial --output text > $keyFile"

# Validate formatting
$lines = Get-Content $keyFile
Write-Host "Nombre de lignes dans le fichier clé PEM : $($lines.Count)" -ForegroundColor Gray
if ($lines.Count -le 1) {
    Write-Error "Échec : le fichier clé PEM est mal formaté (une seule ligne détectée)."
    exit 1
}

# Secure the pem file permissions for Windows OpenSSH
Write-Host "Configuration des permissions sécurisées de la clé PEM..." -ForegroundColor Cyan
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls.exe $keyFile /inheritance:r | Out-Null
icacls.exe $keyFile /grant "${currentUser}:F" | Out-Null

# 3. Security Group reuse or recreation
$sgName = "grh-sg"
$vpcId = "vpc-09c013bc84dfc095f"

Write-Host "Vérification du groupe de sécurité $sgName..." -ForegroundColor Cyan
$sgId = aws ec2 describe-security-groups --filters "Name=group-name,Values=$sgName" "Name=vpc-id,Values=$vpcId" --query "SecurityGroups[0].GroupId" --output text

if ($sgId -and $sgId -ne "None" -and $sgId -ne "") {
    Write-Host "Groupe de sécurité existant trouvé : $sgId. Réutilisation." -ForegroundColor Yellow
} else {
    Write-Host "Création du groupe de sécurité $sgName..." -ForegroundColor Cyan
    $sgId = aws ec2 create-security-group --group-name $sgName --description "Security group for GRH application" --vpc-id $vpcId --query "GroupId" --output text
    
    Write-Host "Configuration des ports (22: SSH et 80: HTTP)..." -ForegroundColor Cyan
    aws ec2 authorize-security-group-ingress --group-id $sgId --protocol tcp --port 22 --cidr 0.0.0.0/0 | Out-Null
    aws ec2 authorize-security-group-ingress --group-id $sgId --protocol tcp --port 80 --cidr 0.0.0.0/0 | Out-Null
}

# 4. Spin up new EC2 instance
$amiId = "ami-0d7405d05f836d0d4"
$instanceType = "t3.micro"

Write-Host "Lancement de la nouvelle instance EC2 $instanceType..." -ForegroundColor Cyan
$instanceId = aws ec2 run-instances --image-id $amiId --instance-type $instanceType --key-name $keyName --security-group-ids $sgId --user-data file://userdata.sh --query "Instances[0].InstanceId" --output text

if (-not $instanceId -or $instanceId -eq "") {
    Write-Error "Échec du lancement de l'instance EC2."
    exit 1
}

Write-Host "ID de la nouvelle instance : $instanceId" -ForegroundColor Green
Write-Host "Attente du démarrage de l'instance..." -ForegroundColor Cyan
aws ec2 wait instance-running --instance-ids $instanceId

Write-Host "Récupération de l'adresse IP publique..." -ForegroundColor Cyan
$ip = aws ec2 describe-instances --instance-ids $instanceId --query "Reservations[0].Instances[0].PublicIpAddress" --output text

# Save details for the ship script
$ip | Out-File -FilePath "..\instance_ip.txt" -Encoding ascii -NoNewline
$instanceId | Out-File -FilePath "..\instance_id.txt" -Encoding ascii -NoNewline

Write-Host "=== Nouvelle infrastructure prête ! ===" -ForegroundColor Green
Write-Host "Adresse IP publique de l'instance : $ip" -ForegroundColor Green
Write-Host "Clé privée : $keyFile" -ForegroundColor Green
