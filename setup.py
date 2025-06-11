from setuptools import setup, find_packages
from pathlib import Path
import os
import shutil
import subprocess

# Read the content of README file
this_directory = Path(__file__).parent
long_description = (this_directory / "README.md").read_text()

# Ensure the JAR file is available
jar_dir = Path("python/coral_server/jar")
jar_file = jar_dir / "coral-server-1.0-SNAPSHOT.jar"

# Check if we need to build the JAR file
if not jar_file.exists():
    print("Building JAR file...")
    # Check if the JAR exists in the build directory
    build_jar = Path("build/libs/coral-server-1.0-SNAPSHOT.jar")
    
    if not build_jar.exists():
        # Build it using Gradle
        if os.name == 'nt':  # Windows
            gradle_cmd = "gradlew.bat"
        else:  # Unix/Linux/Mac
            gradle_cmd = "./gradlew"
        
        try:
            subprocess.run([gradle_cmd, "build"], check=True)
        except subprocess.CalledProcessError:
            print("Failed to build JAR file. Please build it manually using './gradlew build'")
            exit(1)
    
    # Copy the JAR file to the package directory
    os.makedirs(jar_dir, exist_ok=True)
    shutil.copy2(build_jar, jar_file)

setup(
    name="coral-server",
    version="0.1.0",
    description="Python wrapper for Coral Protocol Server",
    long_description=long_description,
    long_description_content_type="text/markdown",
    author="Coral Protocol",
    author_email="hello@coralprotocol.org",
    url="https://github.com/Coral-Protocol/coral-server",
    packages=find_packages(where="python"),
    package_dir={"":"python"},
    include_package_data=True,
    package_data={
        "coral_server": ["jar/*.jar"],
    },
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
    ],
    python_requires=">=3.7",
    install_requires=[
        # No additional Python dependencies required
    ],
    entry_points={
        "console_scripts": [
            "coral-server=coral_server.cli:main",
        ],
    },
)