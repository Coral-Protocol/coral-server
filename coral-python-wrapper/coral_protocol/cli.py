import subprocess
import os
import sys
import argparse

def get_jar_path():
    """Get the path to the JAR file from the installed package."""
    package_dir = os.path.dirname(os.path.abspath(__file__))
    jar_path = os.path.join(package_dir, "jar", "coral-server-1.0-SNAPSHOT.jar")
    
    if not os.path.exists(jar_path):
        raise FileNotFoundError(
            "Could not find coral-server JAR file in the installed package.\n"
            "Please ensure the package was installed correctly."
        )
    return jar_path

def run_server(args):
    """Run the server with the specified arguments."""
    try:
        jar_path = get_jar_path()
        
        if args.stdio:
            command = ["java", "-jar", jar_path, "--stdio"]
        else:
            command = ["java", "-jar", jar_path, "--sse-server-ktor", str(args.port)]
            
        print(f"Starting server with command: {' '.join(command)}")
        subprocess.run(command, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Error running Coral Server: {e}", file=sys.stderr)
        sys.exit(1)
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

def parse_args():
    parser = argparse.ArgumentParser(description="Coral Protocol Server")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--stdio", action="store_true", help="Run server in STDIO mode")
    group.add_argument("--port", type=int, default=3001, help="Port for SSE server (default: 3001)")
 
    return parser.parse_args()

def main():
    args = parse_args()
    
    try:
        run_server(args)
    except KeyboardInterrupt:
        print("\nServer stopped by user")
        sys.exit(0)

if __name__ == "__main__":
    main() 