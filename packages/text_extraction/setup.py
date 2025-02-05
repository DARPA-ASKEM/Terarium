from setuptools import setup, find_packages

setup(
    name="text_extraction_task",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[],
    entry_points={
        "console_scripts": [
            "text_extraction_task:extract_text=tasks.extract_text:main",
        ],
    },
    python_requires=">=3.10",
)
