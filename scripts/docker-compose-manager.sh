#!/bin/bash

# Docker Compose Management Script for Distributed Lock Research Application
# Provides easy management of the multi-service Docker environment

set -e

# Configuration
COMPOSE_PROJECT_NAME="distributed-lock-research"
COMPOSE_FILES="-f docker-compose.yml"
DEFAULT_PROFILE=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_header() {
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}========================================${NC}"
}

show_usage() {
    echo "Docker Compose Management Script for Distributed Lock Research"
    echo ""
    echo "Usage: $0 COMMAND [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  up [PROFILE]           Start all services (default: core services only)"
    echo "  down                   Stop and remove all services"
    echo "  restart [SERVICE]      Restart all services or specific service"
    echo "  logs [SERVICE]         Show logs for all services or specific service"
    echo "  status                 Show status of all services"
    echo "  build [SERVICE]        Build all services or specific service"
    echo "  scale SERVICE=NUM      Scale a service to NUM instances"
    echo "  exec SERVICE COMMAND   Execute command in running service"
    echo "  clean                  Clean up containers, networks, and volumes"
    echo "  health                 Check health of all services"
    echo "  monitor                Start with monitoring stack (Prometheus + Grafana)"
    echo ""
    echo "Profiles:"
    echo "  (none)                 Core services: Redis, ZooKeeper, Apps, Nginx"
    echo "  monitoring             Add Prometheus and Grafana"
    echo ""
    echo "Examples:"
    echo "  $0 up                  # Start core services"
    echo "  $0 up monitoring       # Start with monitoring"
    echo "  $0 logs app1           # Show logs for app1"
    echo "  $0 scale app1=2        # Scale app1 to 2 instances"
    echo "  $0 exec app1 bash      # Open bash in app1 container"
    echo "  $0 health              # Check health of all services"
}

# Check if docker-compose is available
check_docker_compose() {
    if command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE="docker-compose"
    elif docker compose version &> /dev/null; then
        DOCKER_COMPOSE="docker compose"
    else
        log_error "Docker Compose is not installed or not available"
        exit 1
    fi
}

# Build compose command with profiles
build_compose_command() {
    local profile="$1"
    local cmd="$DOCKER_COMPOSE -p $COMPOSE_PROJECT_NAME $COMPOSE_FILES"
    
    if [[ -n "$profile" ]]; then
        cmd="$cmd --profile $profile"
    fi
    
    echo "$cmd"
}

# Start services
start_services() {
    local profile="$1"
    local compose_cmd=$(build_compose_command "$profile")
    
    log_header "Starting Distributed Lock Research Services"
    
    if [[ -n "$profile" ]]; then
        log_info "Starting with profile: $profile"
    else
        log_info "Starting core services"
    fi
    
    log_info "Building images if needed..."
    $compose_cmd build
    
    log_info "Starting services..."
    $compose_cmd up -d
    
    log_success "Services started successfully!"
    
    # Show service status
    show_status
    
    # Show access information
    echo ""
    log_info "Service Access Information:"
    echo "  - Load Balancer (Nginx): http://localhost"
    echo "  - App Instance 1: http://localhost:8081"
    echo "  - App Instance 2: http://localhost:8082"
    echo "  - App Instance 3: http://localhost:8083"
    echo "  - Redis: localhost:6379"
    echo "  - ZooKeeper: localhost:2181"
    
    if [[ "$profile" == "monitoring" ]]; then
        echo "  - Prometheus: http://localhost:9090"
        echo "  - Grafana: http://localhost:3000 (admin/admin123)"
    fi
}

# Stop services
stop_services() {
    local compose_cmd=$(build_compose_command)
    
    log_header "Stopping Distributed Lock Research Services"
    
    log_info "Stopping services..."
    $compose_cmd down
    
    log_success "Services stopped successfully!"
}

# Restart services
restart_services() {
    local service="$1"
    local compose_cmd=$(build_compose_command)
    
    if [[ -n "$service" ]]; then
        log_header "Restarting Service: $service"
        $compose_cmd restart "$service"
        log_success "Service $service restarted successfully!"
    else
        log_header "Restarting All Services"
        $compose_cmd restart
        log_success "All services restarted successfully!"
    fi
}

# Show logs
show_logs() {
    local service="$1"
    local compose_cmd=$(build_compose_command)
    
    if [[ -n "$service" ]]; then
        log_info "Showing logs for service: $service"
        $compose_cmd logs -f "$service"
    else
        log_info "Showing logs for all services"
        $compose_cmd logs -f
    fi
}

# Show status
show_status() {
    local compose_cmd=$(build_compose_command)
    
    log_header "Service Status"
    $compose_cmd ps
}

# Build services
build_services() {
    local service="$1"
    local compose_cmd=$(build_compose_command)
    
    if [[ -n "$service" ]]; then
        log_header "Building Service: $service"
        $compose_cmd build "$service"
        log_success "Service $service built successfully!"
    else
        log_header "Building All Services"
        $compose_cmd build
        log_success "All services built successfully!"
    fi
}

# Scale service
scale_service() {
    local scale_arg="$1"
    local compose_cmd=$(build_compose_command)
    
    log_header "Scaling Service"
    log_info "Scaling: $scale_arg"
    
    $compose_cmd up -d --scale "$scale_arg"
    
    log_success "Service scaled successfully!"
    show_status
}

# Execute command in service
exec_command() {
    local service="$1"
    shift
    local command="$@"
    local compose_cmd=$(build_compose_command)
    
    log_info "Executing command in $service: $command"
    $compose_cmd exec "$service" $command
}

# Clean up
clean_up() {
    local compose_cmd=$(build_compose_command)
    
    log_header "Cleaning Up Docker Resources"
    
    log_warning "This will remove all containers, networks, and volumes!"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Stopping and removing containers..."
        $compose_cmd down -v --remove-orphans
        
        log_info "Removing unused Docker resources..."
        docker system prune -f
        
        log_success "Cleanup completed!"
    else
        log_info "Cleanup cancelled"
    fi
}

# Check health
check_health() {
    local compose_cmd=$(build_compose_command)
    
    log_header "Health Check"
    
    # Get container health status
    containers=$($compose_cmd ps --services)
    
    for container in $containers; do
        health_status=$($compose_cmd ps "$container" --format "table {{.Name}}\t{{.Status}}" | tail -n +2)
        if [[ -n "$health_status" ]]; then
            if echo "$health_status" | grep -q "healthy"; then
                log_success "$container: Healthy"
            elif echo "$health_status" | grep -q "unhealthy"; then
                log_error "$container: Unhealthy"
            elif echo "$health_status" | grep -q "Up"; then
                log_info "$container: Running (no health check)"
            else
                log_warning "$container: $health_status"
            fi
        fi
    done
    
    # Test application endpoints
    echo ""
    log_info "Testing application endpoints..."
    
    for port in 8081 8082 8083; do
        if curl -f -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            log_success "App on port $port: Responding"
        else
            log_error "App on port $port: Not responding"
        fi
    done
    
    # Test load balancer
    if curl -f -s "http://localhost/actuator/health" > /dev/null 2>&1; then
        log_success "Load Balancer: Responding"
    else
        log_error "Load Balancer: Not responding"
    fi
}

# Main script logic
main() {
    check_docker_compose
    
    case "${1:-}" in
        up)
            start_services "$2"
            ;;
        down)
            stop_services
            ;;
        restart)
            restart_services "$2"
            ;;
        logs)
            show_logs "$2"
            ;;
        status)
            show_status
            ;;
        build)
            build_services "$2"
            ;;
        scale)
            if [[ -z "$2" ]]; then
                log_error "Scale argument required (e.g., app1=2)"
                exit 1
            fi
            scale_service "$2"
            ;;
        exec)
            if [[ -z "$2" ]]; then
                log_error "Service name required"
                exit 1
            fi
            service="$2"
            shift 2
            exec_command "$service" "$@"
            ;;
        clean)
            clean_up
            ;;
        health)
            check_health
            ;;
        monitor)
            start_services "monitoring"
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            log_error "Unknown command: ${1:-}"
            echo ""
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"