#!/bin/bash

# Taizi Version Management Script
# This script helps manage semantic versioning and create releases

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Function to validate semantic version
validate_semantic_version() {
    local version=$1
    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
        print_error "Invalid semantic version format: $version"
        print_error "Expected format: MAJOR.MINOR.PATCH[-PRERELEASE]"
        exit 1
    fi
    print_success "Semantic version validated: $version"
}

# Function to get current version
get_current_version() {
    grep -E "versionName\s*=" app/build.gradle.kts | grep -oE '"[^"]+"' | head -1 | tr -d '"'
}

# Function to get current version code
get_current_version_code() {
    grep -E "versionCode\s*=" app/build.gradle.kts | grep -oE '[0-9]+'
}

# Function to increment version
increment_version() {
    local version=$1
    local part=$2

    IFS='.' read -ra VERSION_PARTS <<< "$version"

    case $part in
        major)
            VERSION_PARTS[0]=$((${VERSION_PARTS[0]} + 1))
            VERSION_PARTS[1]=0
            VERSION_PARTS[2]=0
            ;;
        minor)
            VERSION_PARTS[1]=$((${VERSION_PARTS[1]} + 1))
            VERSION_PARTS[2]=0
            ;;
        patch)
            VERSION_PARTS[2]=$((${VERSION_PARTS[2]} + 1))
            ;;
        *)
            print_error "Invalid version part: $part (use major, minor, or patch)"
            exit 1
            ;;
    esac

    echo "${VERSION_PARTS[0]}.${VERSION_PARTS[1]}.${VERSION_PARTS[2]}"
}

# Main menu
case "${1:-help}" in
    validate)
        if [ -z "$2" ]; then
            print_error "Please provide a version to validate"
            echo "Usage: $0 validate <version>"
            exit 1
        fi
        validate_semantic_version "$2"
        ;;

    get-current)
        print_success "Current version: $(get_current_version)"
        ;;

    increment-major)
        local current=$(get_current_version)
        local new_version=$(increment_version "$current" major)
        print_success "New version: $new_version (major increment)"
        ;;

    increment-minor)
        local current=$(get_current_version)
        local new_version=$(increment_version "$current" minor)
        print_success "New version: $new_version (minor increment)"
        ;;

    increment-patch)
        local current=$(get_current_version)
        local new_version=$(increment_version "$current" patch)
        print_success "New version: $new_version (patch increment)"
        ;;

    build)
        print_warning "Building release APK..."
        ./gradlew assembleRelease
        print_success "Build complete. APK located at: app/build/outputs/apk/release/app-release.apk"
        ;;

    check-version)
        print_success "Current version: $(get_current_version)"
        print_success "Current version code: $(get_current_version_code)"
        ;;

    validate-all)
        print_warning "Validating current version..."
        validate_semantic_version "$(get_current_version)"
        print_warning "Checking version code..."
        current_code=$(get_current_version_code)
        print_success "Version code: $current_code"
        ;;

    *)
        echo "Taizi Version Management Script"
        echo ""
        echo "Usage: $0 <command> [options]"
        echo ""
        echo "Commands:"
        echo "  validate <version>    Validate a semantic version format"
        echo "  get-current           Display current version"
        echo "  increment-major       Increment major version"
        echo "  increment-minor       Increment minor version"
        echo "  increment-patch       Increment patch version"
        echo "  build                 Build release APK"
        echo "  check-version         Display current version and code"
        echo "  validate-all          Validate current version and code"
        echo ""
        echo "Examples:"
        echo "  $0 validate 1.0.4"
        echo "  $0 increment-patch"
        echo "  $0 build"
        exit 1
        ;;
esac