import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/SmartSpawner'
const DISCORD = 'https://discord.gg/zrnyG4CuuT'

const manualSidebar = [
  {
    text: 'Getting Started',
    items: [
      { text: 'Overview', link: '/docs/' },
      { text: 'Installation', link: '/docs/installation' },
      { text: 'Download', link: '/docs/download' }
    ]
  },
  {
    text: 'Server Guide',
    items: [
      { text: 'Commands', link: '/docs/commands' },
      { text: 'Permissions', link: '/docs/permissions' }
    ]
  },
  {
    text: 'Configuration',
    collapsed: false,
    items: [
      { text: 'Main Config', link: '/docs/configuration' },
      { text: 'Spawner Settings', link: '/docs/spawners-settings' },
      { text: 'Item Spawner Settings', link: '/docs/item-spawners-settings' },
      { text: 'GUI Layout', link: '/docs/gui-layout' }
    ]
  },
  {
    text: 'Features',
    collapsed: true,
    items: [
      { text: 'Overview', link: '/docs/features/' },
      { text: 'Spawner Types', link: '/docs/features/spawner-types' },
      { text: 'Stacking System', link: '/docs/features/stacking-system' },
      { text: 'GUI System', link: '/docs/features/gui-system' },
      { text: 'Mineable Spawners', link: '/docs/features/mineable-spawners' },
      { text: 'Shop Integration', link: '/docs/features/shop-integration' },
      { text: 'Plugin Compatibility', link: '/docs/features/plugin-compatibility' },
      { text: 'Visual Effects', link: '/docs/features/visual-effects' },
      { text: 'Database Support', link: '/docs/features/database-support' },
      { text: 'Action Logging', link: '/docs/features/action-logging' }
    ]
  },
  {
    text: 'Integrations',
    collapsed: true,
    items: [
      { text: 'Overview', link: '/docs/integrations/' },
      { text: 'Compatibility Matrix', link: '/docs/integrations/compatibility-matrix' },
      { text: 'Shops and Economy', link: '/docs/integrations/shops-economy' },
      { text: 'Protections and Islands', link: '/docs/integrations/protections-islands' },
      { text: 'Bedrock Support', link: '/docs/integrations/bedrock-support' },
      { text: 'AuraSkills', link: '/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/docs/integrations/mythicmobs' },
      { text: 'Troubleshooting', link: '/docs/integrations/troubleshooting' }
    ]
  }
]

const developerSidebar = [
  {
    text: 'Developer API',
    items: [
      { text: 'Overview', link: '/docs/developer-api/' },
      { text: 'Installation', link: '/docs/developer-api/installation' },
      { text: 'API Creation', link: '/docs/developer-api/creation' },
      { text: 'Data Access', link: '/docs/developer-api/data-access' },
      { text: 'Events', link: '/docs/developer-api/events' },
      { text: 'GUI Layout API', link: '/docs/developer-api/gui-layout' },
      { text: 'Validation', link: '/docs/developer-api/validation' },
      { text: 'Examples', link: '/docs/developer-api/examples' }
    ]
  }
]

const changelogSidebar = [
  {
    text: 'Changelog',
    items: [
      { text: 'Release History', link: '/docs/changelog' }
    ]
  }
]

export default defineConfig({
  title: 'SmartSpawner',
  description: 'High-performance GUI spawner management for modern Minecraft servers.',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }],
    ['link', { rel: 'icon', type: 'image/png', href: '/logo.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/logo.png' }],
    ['meta', { property: 'og:image', content: 'https://docs.smartspawner.site/banner.png' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: 'https://docs.smartspawner.site/banner.png' }]
  ],
  themeConfig: {
    logo: '/logo.png',
    externalLinkIcon: true,

    nav: [
      { text: 'Home', link: '/', activeMatch: '^/$' },
      { text: 'Docs', link: '/docs/', activeMatch: '^/docs/(?!developer-api|changelog)' },
      { text: 'Developer API', link: '/docs/developer-api/', activeMatch: '^/docs/developer-api' },
      { component: 'VersionDropdown' }
    ],

    sidebar: {
      '/docs/developer-api/': developerSidebar,
      '/docs/changelog': changelogSidebar,
      '/docs/': manualSidebar
    },

    socialLinks: [
      { icon: 'github', link: REPO },
      { icon: 'discord', link: DISCORD }
    ],

    search: {
      provider: 'local'
    },

    editLink: {
      pattern: 'https://github.com/OpenVdra/SmartSpawner/edit/main/docs/:path',
      text: 'Edit this page on GitHub'
    },

    outline: {
      level: [2, 3],
      label: 'On this page'
    },

    docFooter: {
      prev: 'Previous page',
      next: 'Next page'
    },

    lastUpdated: {
      text: 'Last updated',
      formatOptions: {
        dateStyle: 'medium',
        timeStyle: 'short'
      }
    }
  }
})
