# Platform-Specific Setup Guides

This document provides comprehensive setup instructions for the FAH-ResourceDonor plugin across different hosting environments.

## Pterodactyl Panel Setup

### Automatic Detection
The plugin automatically detects Pterodactyl Panel environments and applies appropriate settings:
- Disables FAH control and web ports for panel compatibility
- Uses file-based core allocation management
- Applies conservative resource limits (max 50% of allocated cores)

### Manual Configuration
If auto-detection fails, manually configure for Pterodactyl:

```yaml
server:
  environment:
    auto-detect: false
    platform: "pterodactyl"
    strict-limits: true

folding-at-home:
  ports:
    control-port: 0
    web-port: 0
    no-port-mode: "file-based"
```

### Pterodactyl Panel Best Practices
1. **Resource Allocation**: The plugin automatically respects your server's allocated CPU and memory limits
2. **Performance Monitoring**: Monitor your server's TPS and adjust `allocation.dynamic.cores-per-player` if needed
3. **Panel Compatibility**: The plugin uses file-based control to avoid port conflicts with the panel
4. **Auto-Updates**: FAH client updates are handled automatically without panel interference

### Environment Variables
The plugin automatically detects these Pterodactyl environment variables:
- `P_SERVER_UUID`: Server identification
- `P_SERVER_LOCATION`: Server location
- `SERVER_MEMORY`: Memory allocation limit
- `SERVER_JARFILE`: Server jar file

## Docker Container Setup

### Automatic Detection
The plugin detects Docker containers by checking:
- `/.dockerenv` file presence
- Container cgroup information
- `DOCKER_CONTAINER` environment variable

### Docker Compose Example
```yaml
version: '3.8'
services:
  minecraft:
    image: itzg/minecraft-server
    ports:
      - "25565:25565"
      - "36330:36330"  # FAH control port (optional)
      - "7396:7396"    # FAH web interface (optional)
    environment:
      EULA: "TRUE"
      TYPE: "SPIGOT"
      VERSION: "1.21"
      MEMORY: "4G"
    volumes:
      - ./data:/data
      - /tmp:/tmp:rw  # Recommended for FAH temporary files
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 4G
```

### Dockerfile Setup
```dockerfile
FROM itzg/minecraft-server:latest

# Install FAH client (handled automatically by plugin)
# No additional steps needed - plugin handles FAH installation

EXPOSE 25565 36330 7396
```

### Container Resource Limits
The plugin automatically detects and respects:
- CPU limits set via `docker run --cpus` or compose `deploy.resources.limits.cpus`
- Memory limits set via `docker run --memory` or compose `deploy.resources.limits.memory`
- cgroup v1 and v2 resource constraints

### Docker Best Practices
1. **Resource Limits**: Always set appropriate CPU and memory limits
2. **Volume Mounts**: Mount `/tmp` as tmpfs for better FAH performance
3. **Port Mapping**: Map FAH ports if you want external access to the web interface
4. **Health Checks**: Monitor container health and FAH contribution status

## Shared Hosting Setup

### Automatic Detection
The plugin detects shared hosting by checking:
- Common shared hosting directory patterns (`public_html`, `cpanel` paths)
- Limited file system permissions
- Restricted user account patterns

### Configuration for Shared Hosting
```yaml
server:
  environment:
    auto-detect: true  # Usually detects automatically
    strict-limits: true

  # Conservative settings for shared hosting
  total-cores: 2  # Most shared hosts limit this
  reserved-cores: 1

folding-at-home:
  ports:
    control-port: 0     # Disabled for shared hosting
    web-port: 0         # Disabled for shared hosting
    no-port-mode: "file-based"

allocation:
  mode: "dynamic"
  dynamic:
    cores-per-player: 0.2  # Very conservative
    max-cores-for-fah: 1   # Only 1 core max
```

### Shared Hosting Limitations
- **CPU Cores**: Limited to 1 core maximum to avoid host issues
- **Memory**: Capped at 512MB for FAH processes
- **Ports**: All additional ports disabled
- **Performance**: Limited contribution potential due to resource restrictions

### Recommended Shared Hosting Providers
For better FAH performance, consider these shared hosting providers that are more resource-friendly:
- Providers with dedicated CPU cores
- VPS upgrade options available
- No strict process monitoring

## VPS/Dedicated Server Setup

### Automatic Detection
Detected when no containerization or hosting restrictions are found.

### Full Configuration Example
```yaml
server:
  total-cores: 8
  reserved-cores: 2

folding-at-home:
  ports:
    control-port: 36330
    web-port: 7396
    no-port-mode: "socket"

  account:
    username: "YourFAHUsername"
    team-id: "123456"
    passkey: "your32characterpasskey"

allocation:
  mode: "dynamic"
  dynamic:
    cores-per-player: 0.5
    max-cores-for-fah: 6

monitoring:
  tps-monitoring: true
  min-tps: 18.0
```

### VPS/Dedicated Best Practices
1. **Resource Allocation**: Use 70-80% of cores for optimal balance
2. **Monitoring**: Enable TPS monitoring to ensure server performance
3. **Web Interface**: Access FAH web interface at `http://yourserver:7396`
4. **Firewall**: Ensure ports 36330 and 7396 are open if using web interface

## Common Configuration Options

### Environment Override Settings
```yaml
server:
  environment:
    # Disable auto-detection and manually set platform
    auto-detect: false
    platform: "vps"  # Options: pterodactyl, docker, shared, vps
    
    # Safety features
    strict-limits: true
    respect-container-limits: true
```

### Performance Tuning by Environment

#### High-Performance (VPS/Dedicated)
```yaml
allocation:
  mode: "dynamic"
  dynamic:
    cores-per-player: 0.8
    max-cores-for-fah: 14  # Adjust based on total cores

monitoring:
  check-interval: 15  # More frequent checks
  tps-monitoring: true
  min-tps: 19.0
```

#### Conservative (Pterodactyl/Docker)
```yaml
allocation:
  mode: "dynamic"
  dynamic:
    cores-per-player: 0.3
    max-cores-for-fah: 4

monitoring:
  check-interval: 60  # Less frequent checks
  tps-monitoring: true
  min-tps: 18.0
```

#### Minimal (Shared Hosting)
```yaml
allocation:
  mode: "dynamic"
  dynamic:
    cores-per-player: 0.1
    max-cores-for-fah: 1

monitoring:
  check-interval: 120  # Infrequent checks
  tps-monitoring: false  # Disabled to reduce overhead
```

## Troubleshooting by Environment

### Pterodactyl Panel Issues
- **Port conflicts**: Ensure control-port and web-port are set to 0
- **Resource limits**: Check panel CPU/memory allocation
- **File permissions**: Verify plugin can write to data folder

### Docker Container Issues  
- **Resource detection**: Check if container limits are properly set
- **Port mapping**: Ensure ports are mapped in container configuration
- **Volume mounts**: Verify persistent storage for FAH data

### Shared Hosting Issues
- **Resource restrictions**: Provider may kill processes using too many resources
- **File permissions**: Limited write access in shared environments
- **Process limits**: Some hosts limit background processes

### General Troubleshooting
1. **Check logs**: Look for environment detection messages in server logs
2. **Verify configuration**: Use `/fah status` command to check current settings
3. **Test allocation**: Monitor core usage with `/fah cores` command
4. **Performance impact**: Use `/fah stats` to monitor contribution vs server performance

## Migration Between Environments

### From Shared Hosting to VPS
1. Copy plugin configuration
2. Update environment settings to allow more resources
3. Enable ports for web interface access
4. Increase core allocation limits

### From VPS to Container
1. Adjust resource limits in container configuration
2. Map necessary ports in container setup
3. Update volume mounts for persistent data
4. Test resource detection in containerized environment

### Environment-Specific Commands
- `/fah environment` - Show detected environment information
- `/fah limits` - Display current resource limits
- `/fah platform` - Show platform-specific settings
- `/fah optimize` - Apply environment-appropriate optimizations