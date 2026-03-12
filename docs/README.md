
# SmartSpawner Documentation

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Astro](https://img.shields.io/badge/Astro-5.13.2-orange)](https://astro.build/)
[![Starlight](https://img.shields.io/badge/Starlight-0.35.2-purple)](https://starlight.astro.build/)

This repository hosts the official documentation website for **[SmartSpawner](https://modrinth.com/plugin/smart-spawner-plugin)**, a powerful Minecraft plugin designed to enhance mob spawner management on servers.

## About SmartSpawner

SmartSpawner is a feature-rich plugin that simplifies and enhances the management of mob spawners in Minecraft servers. It offers advanced customization options, seamless integrations with popular plugins, and a robust developer API for extending functionality. Notably, its GUI-based system generates mob drops and experience without spawning entities, significantly boosting server performance.

## Site Overview

- **Framework**: Astro with Starlight theme
- **Version**: 0.0.1
- **Primary Dependencies**:
  - Astro (^5.13.2)
  - @astrojs/starlight (^0.35.2)
  - Starlight themes (Catppuccin, Next, Obsidian)
  - Sharp (^0.34.2)

## Quick Start

### Prerequisites
- Node.js (version 18 or higher)
- npm or yarn package manager

### Local Development

1. **Clone the repository**:
   ```bash
   git clone https://github.com/NighterDevelopment/smartspawner-docs.git
   cd smartspawner-docs
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Start the development server**:
   ```bash
   npm run dev
   ```
   The site will be available at `http://localhost:4321`.

### Build and Preview

- **Build for production**:
  ```bash
  npm run build
  ```

- **Preview the production build**:
  ```bash
  npm run preview
  ```

## Project Structure

```
smartspawner-docs/
├── src/
│   ├── content/
│   │   ├── docs/          # Main documentation files (Markdown/MDX)
│   │   └── config.ts      # Content configuration
│   ├── components/        # Custom Astro components
│   ├── styles/            # Custom CSS styles
│   └── assets/            # Static assets
├── public/                # Public static files
├── astro.config.mjs       # Astro configuration
├── package.json           # Project dependencies and scripts
└── tsconfig.json          # TypeScript configuration
```

## Contributing

We appreciate contributions from the community! Whether you're fixing a typo, adding new documentation, or improving the site's functionality, your input is valuable.

### Contribution Guidelines

1. **Fork the repository** on GitHub.
2. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**:
   - Documentation updates go in `src/content/docs/`
   - Component changes in `src/components/`
   - Style modifications in `src/styles/`
4. **Test your changes** locally using `npm run dev`.
5. **Update timestamps for documentation files** (if you modified any `.md` files in `src/content/docs/`):

   ```bash
   npm run add-timestamps
   ```
   This command automatically updates the "Last update" timestamp at the end of each modified documentation file based on its last modification time. This ensures accurate update information for readers
6. **Commit your changes** with descriptive messages:
   ```bash
   git commit -m "Add comprehensive guide for plugin integrations"
   ```
7. **Push to your fork** and **create a pull request**.

### Code Style
- Follow existing Markdown formatting and structure.
- Use clear, concise language suitable for both beginners and advanced users.
- Include code examples where applicable.
- Test all links and ensure they are functional.

For significant changes or new features, please open an issue first to discuss the proposed modifications.

## Deployment

This site is configured for deployment to GitHub Pages:

```bash
npm run deploy
```

This command builds the site and deploys it using `gh-pages`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support and Contact

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/NighterDevelopment/smartspawner-docs/issues)
- **Discussions**: Join community discussions on [Discord Server](https://dsc.gg/nighterdevelopment)

For questions about the SmartSpawner plugin itself, please refer to the main plugin repository or community channels.

---

*Built with ❤️ using Astro and Starlight*
