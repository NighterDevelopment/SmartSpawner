import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/SmartSpawner'
const DISCORD = 'https://discord.gg/zrnyG4CuuT'
const JAVADOCS = 'https://docs.smartspawner.site/javadocs/'

const enManualSidebar = [
  {
    text: 'Getting Started',
    items: [
      { text: 'Overview', link: '/docs/' },
      { text: 'Installation', link: '/docs/installation' },
      { text: 'Download', link: '/docs/download' },
      { text: 'FAQ', link: '/docs/faq' }
    ]
  },
  {
    text: 'Server Guide',
    items: [
      { text: 'Plugin Compatibility', link: '/docs/plugin-compatibility' },
      { text: 'Commands', link: '/docs/commands' },
      { text: 'Permissions', link: '/docs/permissions' },
      { text: 'Database Support', link: '/docs/database-support' },
      { text: 'Action Logging', link: '/docs/action-logging' }
    ]
  },
  {
    text: 'Configuration',
    items: [
      { text: 'Main Config', link: '/docs/configuration' },
      { text: 'Sell Integration', link: '/docs/sell-integration' },
      { text: 'Mob Spawners', link: '/docs/spawner-mobs' },
      { text: 'Item Spawners', link: '/docs/spawner-items' },
      { text: 'GUI Layout', link: '/docs/gui-layout' }
    ]
  },
  {
    text: 'Integrations',
    items: [
      { text: 'Overview', link: '/docs/integrations/' },
      {
        text: 'Shops and Economy',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/shops/' },
          { text: 'EconomyShopGUI', link: '/docs/integrations/shops/economyshopgui' },
          { text: 'ShopGUI+', link: '/docs/integrations/shops/shopguiplus' },
          { text: 'zShop', link: '/docs/integrations/shops/zshop' },
          { text: 'Vault', link: '/docs/integrations/shops/vault' },
          { text: 'ExcellentEconomy', link: '/docs/integrations/shops/excellenteconomy' }
        ]
      },
      {
        text: 'Protections and Claims',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/protections/' },
          { text: 'WorldGuard', link: '/docs/integrations/protections/worldguard' },
          { text: 'GriefPrevention', link: '/docs/integrations/protections/griefprevention' },
          { text: 'Lands', link: '/docs/integrations/protections/lands' },
          { text: 'Towny', link: '/docs/integrations/protections/towny' },
          { text: 'Residence', link: '/docs/integrations/protections/residence' },
          { text: 'RedProtect', link: '/docs/integrations/protections/redprotect' },
          { text: 'SimpleClaimSystem', link: '/docs/integrations/protections/simpleclaimsystem' },
          { text: 'FactionsUUID', link: '/docs/integrations/protections/factionsuuid' },
          { text: 'BlockLocker', link: '/docs/integrations/protections/blocklocker' }
        ]
      },
      {
        text: 'Islands and Plots',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/islands/' },
          { text: 'PlotSquared', link: '/docs/integrations/islands/plotsquared' },
          { text: 'minePlots', link: '/docs/integrations/islands/mineplots' },
          { text: 'SuperiorSkyblock2', link: '/docs/integrations/islands/superiorskyblock2' },
          { text: 'BentoBox', link: '/docs/integrations/islands/bentobox' },
          { text: 'IridiumSkyblock', link: '/docs/integrations/islands/iridiumskyblock' }
        ]
      },
      { text: 'AuraSkills', link: '/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/docs/integrations/mythicmobs' },
      { text: 'Troubleshooting', link: '/docs/integrations/troubleshooting' }
    ]
  }
]

const viManualSidebar = [
  {
    text: 'Bắt đầu',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/' },
      { text: 'Cài đặt', link: '/vi/docs/installation' },
      { text: 'Tải xuống', link: '/vi/docs/download' },
      { text: 'Câu hỏi thường gặp', link: '/vi/docs/faq' }
    ]
  },
  {
    text: 'Hướng dẫn máy chủ',
    items: [
      { text: 'Tương thích plugin', link: '/vi/docs/plugin-compatibility' },
      { text: 'Lệnh', link: '/vi/docs/commands' },
      { text: 'Quyền', link: '/vi/docs/permissions' },
      { text: 'Hỗ trợ cơ sở dữ liệu', link: '/vi/docs/database-support' },
      { text: 'Nhật ký thao tác', link: '/vi/docs/action-logging' }
    ]
  },
  {
    text: 'Cấu hình',
    items: [
      { text: 'Cấu hình chính', link: '/vi/docs/configuration' },
      { text: 'Tích hợp bán', link: '/vi/docs/sell-integration' },
      { text: 'Spawner Mob', link: '/vi/docs/spawner-mobs' },
      { text: 'Item Spawner', link: '/vi/docs/spawner-items' },
      { text: 'Bố cục GUI', link: '/vi/docs/gui-layout' }
    ]
  },
  {
    text: 'Tích hợp',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/integrations/' },
      {
        text: 'Cửa hàng và kinh tế',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/shops/' },
          { text: 'EconomyShopGUI', link: '/vi/docs/integrations/shops/economyshopgui' },
          { text: 'ShopGUI+', link: '/vi/docs/integrations/shops/shopguiplus' },
          { text: 'zShop', link: '/vi/docs/integrations/shops/zshop' },
          { text: 'Vault', link: '/vi/docs/integrations/shops/vault' },
          { text: 'ExcellentEconomy', link: '/vi/docs/integrations/shops/excellenteconomy' }
        ]
      },
      {
        text: 'Bảo vệ và claim',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/protections/' },
          { text: 'WorldGuard', link: '/vi/docs/integrations/protections/worldguard' },
          { text: 'GriefPrevention', link: '/vi/docs/integrations/protections/griefprevention' },
          { text: 'Lands', link: '/vi/docs/integrations/protections/lands' },
          { text: 'Towny', link: '/vi/docs/integrations/protections/towny' },
          { text: 'Residence', link: '/vi/docs/integrations/protections/residence' },
          { text: 'RedProtect', link: '/vi/docs/integrations/protections/redprotect' },
          { text: 'SimpleClaimSystem', link: '/vi/docs/integrations/protections/simpleclaimsystem' },
          { text: 'FactionsUUID', link: '/vi/docs/integrations/protections/factionsuuid' },
          { text: 'BlockLocker', link: '/vi/docs/integrations/protections/blocklocker' }
        ]
      },
      {
        text: 'Đảo và plot',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/islands/' },
          { text: 'PlotSquared', link: '/vi/docs/integrations/islands/plotsquared' },
          { text: 'minePlots', link: '/vi/docs/integrations/islands/mineplots' },
          { text: 'SuperiorSkyblock2', link: '/vi/docs/integrations/islands/superiorskyblock2' },
          { text: 'BentoBox', link: '/vi/docs/integrations/islands/bentobox' },
          { text: 'IridiumSkyblock', link: '/vi/docs/integrations/islands/iridiumskyblock' }
        ]
      },
      { text: 'AuraSkills', link: '/vi/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/vi/docs/integrations/mythicmobs' },
      { text: 'Khắc phục sự cố', link: '/vi/docs/integrations/troubleshooting' }
    ]
  }
]

const enFeaturesSidebar = [
  {
    text: 'Features',
    items: [
      { text: 'Overview', link: '/features/' },
      { text: 'Spawner Types', link: '/features/spawner-types' },
      { text: 'Stacking System', link: '/features/stacking-system' },
      { text: 'GUI System', link: '/features/gui-system' },
      { text: 'Mineable Spawners', link: '/features/mineable-spawners' },
      { text: 'Shop Integration', link: '/features/shop-integration' },
      { text: 'Visual Effects', link: '/features/visual-effects' }
    ]
  }
]

const viFeaturesSidebar = [
  {
    text: 'Tính năng',
    items: [
      { text: 'Tổng quan', link: '/vi/features/' },
      { text: 'Các loại Spawner', link: '/vi/features/spawner-types' },
      { text: 'Hệ thống xếp chồng', link: '/vi/features/stacking-system' },
      { text: 'Hệ thống GUI', link: '/vi/features/gui-system' },
      { text: 'Đào Spawner', link: '/vi/features/mineable-spawners' },
      { text: 'Tích hợp cửa hàng', link: '/vi/features/shop-integration' },
      { text: 'Hiệu ứng trực quan', link: '/vi/features/visual-effects' }
    ]
  }
]

const enDeveloperSidebar = [
  {
    text: 'Developer API',
    items: [
      { text: 'Overview', link: '/developer-api/' },
      { text: 'Installation', link: '/developer-api/installation' },
      { text: 'API Creation', link: '/developer-api/creation' },
      { text: 'Data Access', link: '/developer-api/data-access' },
      { text: 'Events', link: '/developer-api/events' },
      { text: 'GUI Layout API', link: '/developer-api/gui-layout' },
      { text: 'Validation', link: '/developer-api/validation' },
      { text: 'Examples', link: '/developer-api/examples' },
      { text: 'Javadocs', link: JAVADOCS }
    ]
  }
]

const viDeveloperSidebar = [
  {
    text: 'API dành cho lập trình viên',
    items: [
      { text: 'Tổng quan', link: '/vi/developer-api/' },
      { text: 'Cài đặt', link: '/vi/developer-api/installation' },
      { text: 'Khởi tạo API', link: '/vi/developer-api/creation' },
      { text: 'Truy cập dữ liệu', link: '/vi/developer-api/data-access' },
      { text: 'Sự kiện', link: '/vi/developer-api/events' },
      { text: 'API bố cục GUI', link: '/vi/developer-api/gui-layout' },
      { text: 'Kiểm tra', link: '/vi/developer-api/validation' },
      { text: 'Ví dụ', link: '/vi/developer-api/examples' },
      { text: 'Javadocs', link: JAVADOCS }
    ]
  }
]

const enSidebar = {
  '/developer-api/': enDeveloperSidebar,
  '/features/': enFeaturesSidebar,
  '/docs/changelog': [{ text: 'Changelog', items: [{ text: 'Release History', link: '/docs/changelog' }] }],
  '/docs/': enManualSidebar
}

const viSidebar = {
  '/vi/developer-api/': viDeveloperSidebar,
  '/vi/features/': viFeaturesSidebar,
  '/vi/docs/changelog': [{ text: 'Nhật ký thay đổi', items: [{ text: 'Lịch sử phát hành', link: '/vi/docs/changelog' }] }],
  '/vi/docs/': viManualSidebar
}

export default defineConfig({
  title: 'SmartSpawner',
  description: 'High-performance GUI spawner management for modern Minecraft servers.',
  cleanUrls: true,
  lastUpdated: true,
  head: [
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
    socialLinks: [
      { icon: 'github', link: REPO },
      { icon: 'discord', link: DISCORD }
    ],
    search: {
      provider: 'local'
    }
  },
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/', activeMatch: '^/$' },
          { text: 'Docs', link: '/docs/', activeMatch: '^/docs/(?!changelog)' },
          { text: 'Features', link: '/features/', activeMatch: '^/features/' },
          { text: 'Developer API', link: '/developer-api/', activeMatch: '^/developer-api/' },
          { component: 'VersionDropdown' },
          { component: 'LanguageDropdown' }
        ],
        sidebar: enSidebar,
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
    },
    vi: {
      label: 'Tiếng Việt',
      lang: 'vi',
      description: 'Quản lý spawner hiệu năng cao bằng GUI dành cho máy chủ Minecraft hiện đại.',
      themeConfig: {
        nav: [
          { text: 'Trang chủ', link: '/vi/', activeMatch: '^/vi/$' },
          { text: 'Tài liệu', link: '/vi/docs/', activeMatch: '^/vi/docs/(?!changelog)' },
          { text: 'Tính năng', link: '/vi/features/', activeMatch: '^/vi/features/' },
          { text: 'API lập trình', link: '/vi/developer-api/', activeMatch: '^/vi/developer-api/' },
          { component: 'VersionDropdown' },
          { component: 'LanguageDropdown' }
        ],
        sidebar: viSidebar,
        editLink: {
          pattern: 'https://github.com/OpenVdra/SmartSpawner/edit/main/docs/:path',
          text: 'Chỉnh sửa trang này trên GitHub'
        },
        outline: {
          level: [2, 3],
          label: 'Trên trang này'
        },
        docFooter: {
          prev: 'Trang trước',
          next: 'Trang sau'
        },
        lastUpdated: {
          text: 'Cập nhật lần cuối',
          formatOptions: {
            dateStyle: 'medium',
            timeStyle: 'short'
          }
        },
        returnToTopLabel: 'Về đầu trang',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Giao diện',
        lightModeSwitchTitle: 'Chuyển sang giao diện sáng',
        darkModeSwitchTitle: 'Chuyển sang giao diện tối'
      }
    }
  }
})
