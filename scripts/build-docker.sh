#!/bin/bash

# Docker Build Script for Distributed Lock Research Application
# This script provides optimized Docker image building with proper tagging and caching

set -e

# Configuration
IMAGE_NAME="distributed-lock-research"
IMAGE_TAG="${1:-latest}"
REGISTRY="${DOCKER_REGISTRY:-}"
BUILD_ARGS=""
CACHE_FROM=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

show_usage() {
    echo "Usage: $0 [TAG] [OPTIONS]"
    echo ""
    echo "Arguments:"
    echo "  TAG                 Docker image tag (default: latest)"
    echo ""
    echo "Options:"
    echo "  --registry REGISTRY Docker registry prefix"
    echo "  --no-cache         Build without using cache"
    echo "  --push             Push image to registry after build"
    echo "  --multi-platform   Build for multiple platforms (linux/amd64,linux/arm64)"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Build with 'latest' tag"
    echo "  $0 v1.0.0                            # Build with 'v1.0.0' tag"
    echo "  $0 latest --registry myregistry.com  # Build and tag for registry"
    echo "  $0 latest --push                     # Build and push to registry"
    echo "  $0 latest --no-cache                 # Build without cache"
}

# Parse command line arguments
PUSH=false
NO_CACHE=false
MULTI_PLATFORM=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --no-cache)
            NO_CACHE=true
            shift
            ;;
        --push)
            PUSH=true
            shift
            ;;
        --multi-platform)
            MULTI_PLATFORM=true
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        -*)
            log_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
        *)
            if [[ -z "$IMAGE_TAG" || "$IMAGE_TAG" == "latest" ]]; then
                IMAGE_TAG="$1"
            fi
            shift
            ;;
    esac
done

# Construct full image name
if [[ -n "$REGISTRY" ]]; then
    FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
else
    FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
fi

# Build arguments
if [[ "$NO_CACHE" == "true" ]]; then
    BUILD_ARGS="$BUILD_ARGS --no-cache"
else
    # Use cache from previous builds
    CACHE_FROM="--cache-from ${IMAGE_NAME}:latest"
fi

# Platform arguments
PLATFORM_ARGS=""
if [[ "$MULTI_PLATFORM" == "true" ]]; then
    PLATFORM_ARGS="--platform linux/amd64,linux/arm64"
    log_info "Building for multiple platforms: linux/amd64, linux/arm64"
fi

# Pre-build checks
log_info "Starting Docker build process..."
log_info "Image name: $FULL_IMAGE_NAME"
log_info "Build context: $(pwd)"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    log_error "Docker is not running or not accessible"
    exit 1
fi

# Check if Dockerfile exists
if [[ ! -f "Dockerfile" ]]; then
    log_error "Dockerfile not found in current directory"
    exit 1
fi

# Show build information
log_info "Build configuration:"
echo "  - Image: $FULL_IMAGE_NAME"
echo "  - Cache: $([ "$NO_CACHE" == "true" ] && echo "disabled" || echo "enabled")"
echo "  - Push: $([ "$PUSH" == "true" ] && echo "yes" || echo "no")"
echo "  - Multi-platform: $([ "$MULTI_PLATFORM" == "true" ] && echo "yes" || echo "no")"

# Build the image
log_info "Building Docker image..."
BUILD_START_TIME=$(date +%s)

if [[ "$MULTI_PLATFORM" == "true" ]]; then
    # Use buildx for multi-platform builds
    docker buildx build \
        $PLATFORM_ARGS \
        $BUILD_ARGS \
        $CACHE_FROM \
        -t "$FULL_IMAGE_NAME" \
        $([ "$PUSH" == "true" ] && echo "--push" || echo "--load") \
        .
else
    # Standard build
    docker build \
        $BUILD_ARGS \
        $CACHE_FROM \
        -t "$FULL_IMAGE_NAME" \
        .
fi

BUILD_END_TIME=$(date +%s)
BUILD_DURATION=$((BUILD_END_TIME - BUILD_START_TIME))

if [[ $? -eq 0 ]]; then
    log_success "Docker image built successfully in ${BUILD_DURATION}s"
    
    # Show image information
    log_info "Image information:"
    docker images "$FULL_IMAGE_NAME" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
    
    # Push to registry if requested
    if [[ "$PUSH" == "true" && "$MULTI_PLATFORM" != "true" ]]; then
        log_info "Pushing image to registry..."
        docker push "$FULL_IMAGE_NAME"
        if [[ $? -eq 0 ]]; then
            log_success "Image pushed successfully"
        else
            log_error "Failed to push image"
            exit 1
        fi
    fi
    
    # Tag as latest if not already
    if [[ "$IMAGE_TAG" != "latest" ]]; then
        LATEST_TAG="${REGISTRY:+${REGISTRY}/}${IMAGE_NAME}:latest"
        log_info "Tagging as latest: $LATEST_TAG"
        docker tag "$FULL_IMAGE_NAME" "$LATEST_TAG"
    fi
    
    log_success "Build process completed successfully!"
    echo ""
    echo "To run the container:"
    echo "  docker run -p 8080:8080 $FULL_IMAGE_NAME"
    echo ""
    echo "To run with environment variables:"
    echo "  docker run -p 8080:8080 -e LOCK_PROVIDER=redis -e SPRING_PROFILES_ACTIVE=docker $FULL_IMAGE_NAME"
    
else
    log_error "Docker build failed"
    exit 1
fi