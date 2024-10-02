# OpenAI Vision Plugin

## Overview

The OpenAI Vision Plugin is designed to automatically tag images and add alt text descriptions to content in your DotCMS instance. This plugin leverages OpenAI's vision capabilities to enhance the accessibility and searchability of your content.

## Features

- **Auto-Tagging**: Automatically tags images based on their content.
- **Alt Text Generation**: Generates and adds alt text descriptions to images.
- **Workflow Integration**: Integrates with DotCMS workflows to tag and add alt text during content publishing.

## Requirements

- Java
- Maven
- DotCMS
- OpenAI API Key

## Installation

1. **Clone the repository**:
    ```sh
    git clone <repository-url>
    cd <repository-directory>
    ```

2. **Build the project**:
    ```sh
    mvn clean install
    ```

3. **Deploy the plugin**:
    - Upload the generated JAR file from the `target` directory to your dotCMS plugins.

4. **Restart DotCMS** to load the new plugin.

## Configuration

1. **Set up OpenAI API Key**:
    - Navigate to the DotCMS admin panel.
    - Go to `Apps` Screen and 
    - Add your OpenAI API key under the `dotAI` settings.

2. **Configure Content Types**:
    - Define which content types should be auto-tagged and have alt text added.
    - This can be done in the workflow actionlet or in the dotAI App by setting a  property called `AI_VISION_AUTOTAG_CONTENTTYPES` with a comma-separated list of content types that you wat to auto-tag.

## Usage

### Workflow Actionlet

The plugin provides a workflow actionlet that can be added to your workflows to automatically tag and add alt text to images.

1. **Add the Actionlet to a Workflow**:
    - Go to `Workflow` portlet.
    - Edit the desired workflow and add the `Open AI Auto-Tag Images` actionlet to the appropriate steps.

### Event Listener

The plugin also includes an event listener that triggers on content publish events.

1. **Enable the Event Listener**:
    - Ensure the `OpenAIImageTaggingContentListener` is enabled in your DotCMS instance.



### Configs

- `AI_VISION_ALT_TEXT_OPTIONS` - Fields to write the alt text to - defaults to `altText,alt,description`
- `AI_VISION_MODEL` - The model to use for AI vision - defaults to `gpt-4o`
- `AI_VISION_MAX_TOKENS` - The maximum number of tokens to generate for the alt text - defaults to `500`
- `AI_VISION_AUTOTAG_CONTENTTYPES` - The content types to auto-tag - defaults to `image`


## Development

### Prerequisites

- Java 11+
- Maven

### Building the Project

1. **Open the project in IntelliJ IDEA**.
2. **Build the project** using Maven:
    ```sh
    mvn clean install
    ```
