#!/bin/bash

# HarmonySafeAgent Docker Runner Script
# Provides convenient commands for running the Docker container

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
IMAGE_NAME="harmony-safe-agent"
CONTAINER_NAME="harmony-agent"
VERSION="latest"

# Helper functions
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

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker first."
        exit 1
    fi
}

# Build the Docker image
build() {
    log_info "Building HarmonySafeAgent Docker image..."
    docker build -t ${IMAGE_NAME}:${VERSION} .
    log_success "Docker image built successfully: ${IMAGE_NAME}:${VERSION}"
}

# Run interactive mode
interactive() {
    log_info "Starting HarmonySafeAgent in interactive mode..."
    
    # Create necessary directories
    mkdir -p workspace reports cache config
    
    # Check for .env file
    if [ ! -f .env ]; then
        log_warning ".env file not found. Creating from .env.example..."
        cp .env.example .env
        log_warning "Please edit .env file with your API keys before running."
    fi
    
    docker run -it --rm \
        --name ${CONTAINER_NAME} \
        --env-file .env \
        -v "$(pwd)/workspace:/app/workspace:ro" \
        -v "$(pwd)/reports:/app/reports" \
        -v "$(pwd)/cache:/app/cache" \
        -v "$(pwd)/config:/app/config" \
        ${IMAGE_NAME}:${VERSION}
}

# Run analysis on a specific path
analyze() {
    local source_path="$1"
    local output_file="$2"
    
    if [ -z "$source_path" ]; then
        log_error "Usage: $0 analyze <source_path> [output_file]"
        exit 1
    fi
    
    if [ ! -e "$source_path" ]; then
        log_error "Source path does not exist: $source_path"
        exit 1
    fi
    
    log_info "Analyzing: $source_path"
    
    # Prepare arguments
    local args="analyze /app/workspace/$(basename "$source_path")"
    if [ -n "$output_file" ]; then
        args="$args -o /app/reports/$output_file"
    fi
    
    # Create necessary directories
    mkdir -p reports cache
    
    docker run --rm \
        --env-file .env \
        -v "$(realpath "$source_path"):/app/workspace/$(basename "$source_path"):ro" \
        -v "$(pwd)/reports:/app/reports" \
        -v "$(pwd)/cache:/app/cache" \
        ${IMAGE_NAME}:${VERSION} $args
}

# Run strategic analysis
strategic() {
    local source_path="$1"
    
    if [ -z "$source_path" ]; then
        log_error "Usage: $0 strategic <source_path>"
        exit 1
    fi
    
    if [ ! -e "$source_path" ]; then
        log_error "Source path does not exist: $source_path"
        exit 1
    fi
    
    log_info "Running strategic analysis on: $source_path"
    
    # Create necessary directories
    mkdir -p reports cache
    
    docker run -it --rm \
        --env-file .env \
        -v "$(realpath "$source_path"):/app/workspace/$(basename "$source_path"):ro" \
        -v "$(pwd)/reports:/app/reports" \
        -v "$(pwd)/cache:/app/cache" \
        ${IMAGE_NAME}:${VERSION} strategic-analyze "/app/workspace/$(basename "$source_path")"
}

# Show container logs
logs() {
    docker logs -f ${CONTAINER_NAME}
}

# Stop and remove container
stop() {
    log_info "Stopping HarmonySafeAgent container..."
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm ${CONTAINER_NAME} 2>/dev/null || true
    log_success "Container stopped and removed"
}

# Clean up Docker resources
clean() {
    log_info "Cleaning up Docker resources..."
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm ${CONTAINER_NAME} 2>/dev/null || true
    docker rmi ${IMAGE_NAME}:${VERSION} 2>/dev/null || true
    log_success "Cleanup completed"
}

# Show help
help() {
    echo "HarmonySafeAgent Docker Runner"
    echo ""
    echo "Usage: $0 <command> [arguments]"
    echo ""
    echo "Commands:"
    echo "  build                     Build the Docker image"
    echo "  interactive               Start interactive mode"
    echo "  analyze <path> [output]   Analyze source code"
    echo "  strategic <path>          Run strategic analysis"
    echo "  logs                      Show container logs"
    echo "  stop                      Stop and remove container"
    echo "  clean                     Clean up Docker resources"
    echo "  help                      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 build"
    echo "  $0 interactive"
    echo "  $0 analyze ./src/main report.html"
    echo "  $0 strategic ./test-strategic-analysis.c"
    echo ""
    echo "Prerequisites:"
    echo "  - Docker must be installed and running"
    echo "  - Copy .env.example to .env and configure API keys"
    echo "  - Place source code in ./workspace directory for analysis"
}

# Main script logic
main() {
    check_docker
    
    case "${1:-help}" in
        build)
            build
            ;;
        interactive|i)
            interactive
            ;;
        analyze|a)
            analyze "$2" "$3"
            ;;
        strategic|s)
            strategic "$2"
            ;;
        logs|l)
            logs
            ;;
        stop)
            stop
            ;;
        clean)
            clean
            ;;
        help|h|--help|-h)
            help
            ;;
        *)
            log_error "Unknown command: $1"
            help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"