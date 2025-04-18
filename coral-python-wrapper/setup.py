from setuptools import setup, find_packages
import os

with open("README.md", encoding="utf-8") as f:
    long_description = f.read()

setup(
    name="coral-server",
    version="0.0.1",
    packages=find_packages(include=["coral_protocol", "coral_protocol.*"]),
    include_package_data=True,
    package_data={
        "coral_protocol": ["jar/*.jar"]
    },
    entry_points={
        "console_scripts": [
            "coral-server=coral_protocol.cli:main"
        ]
    },
    python_requires=">=3.7",
    install_requires=[
        
    ],
    description="Python wrapper for Coral Protocol Server",
    long_description=long_description,
    long_description_content_type="text/markdown",
    author="Coral Protocol Team",
    url="https://github.com/Coral-Protocol/coral-server",  
    project_urls={
        "Bug Tracker": "https://github.com/Coral-Protocol/coral-server/issues",  
    },
    keywords="coral, protocol, server, wrapper",
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers", 
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Operating System :: OS Independent",
    ],
) 