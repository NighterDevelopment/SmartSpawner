import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/SmartSpawner'
const DISCORD = 'https://discord.gg/zrnyG4CuuT'

const enManualSidebar = [
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
    items: [
      { text: 'Main Config', link: '/docs/configuration' },
      { text: 'Spawner Settings', link: '/docs/spawners-settings' },
      { text: 'Item Spawner Settings', link: '/docs/item-spawners-settings' },
      { text: 'GUI Layout', link: '/docs/gui-layout' }
    ]
  },
  {
    text: 'Features',
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
    items: [
      { text: 'Overview', link: '/docs/integrations/' },
      { text: 'Compatibility Matrix', link: '/docs/integrations/compatibility-matrix' },
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
          { text: 'SimpleClaimSystem', link: '/docs/integrations/protections/simpleclaimsystem' }
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
      { text: 'Bedrock Support', link: '/docs/integrations/bedrock-support' },
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
      { text: 'Tải xuống', link: '/vi/docs/download' }
    ]
  },
  {
    text: 'Hướng dẫn máy chủ',
    items: [
      { text: 'Lệnh', link: '/vi/docs/commands' },
      { text: 'Quyền', link: '/vi/docs/permissions' }
    ]
  },
  {
    text: 'Cấu hình',
    items: [
      { text: 'Cấu hình chính', link: '/vi/docs/configuration' },
      { text: 'Thiết lập Spawner', link: '/vi/docs/spawners-settings' },
      { text: 'Thiết lập Item Spawner', link: '/vi/docs/item-spawners-settings' },
      { text: 'Bố cục GUI', link: '/vi/docs/gui-layout' }
    ]
  },
  {
    text: 'Tính năng',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/features/' },
      { text: 'Các loại Spawner', link: '/vi/docs/features/spawner-types' },
      { text: 'Hệ thống xếp chồng', link: '/vi/docs/features/stacking-system' },
      { text: 'Hệ thống GUI', link: '/vi/docs/features/gui-system' },
      { text: 'Đào Spawner', link: '/vi/docs/features/mineable-spawners' },
      { text: 'Tích hợp cửa hàng', link: '/vi/docs/features/shop-integration' },
      { text: 'Tương thích plugin', link: '/vi/docs/features/plugin-compatibility' },
      { text: 'Hiệu ứng trực quan', link: '/vi/docs/features/visual-effects' },
      { text: 'Hỗ trợ cơ sở dữ liệu', link: '/vi/docs/features/database-support' },
      { text: 'Nhật ký thao tác', link: '/vi/docs/features/action-logging' }
    ]
  },
  {
    text: 'Tích hợp',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/integrations/' },
      { text: 'Ma trận tương thích', link: '/vi/docs/integrations/compatibility-matrix' },
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
          { text: 'SimpleClaimSystem', link: '/vi/docs/integrations/protections/simpleclaimsystem' }
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
      { text: 'Hỗ trợ Bedrock', link: '/vi/docs/integrations/bedrock-support' },
      { text: 'AuraSkills', link: '/vi/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/vi/docs/integrations/mythicmobs' },
      { text: 'Khắc phục sự cố', link: '/vi/docs/integrations/troubleshooting' }
    ]
  }
]

const enDeveloperSidebar = [
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

const viDeveloperSidebar = [
  {
    text: 'API dành cho lập trình viên',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/developer-api/' },
      { text: 'Cài đặt', link: '/vi/docs/developer-api/installation' },
      { text: 'Khởi tạo API', link: '/vi/docs/developer-api/creation' },
      { text: 'Truy cập dữ liệu', link: '/vi/docs/developer-api/data-access' },
      { text: 'Sự kiện', link: '/vi/docs/developer-api/events' },
      { text: 'API bố cục GUI', link: '/vi/docs/developer-api/gui-layout' },
      { text: 'Kiểm tra', link: '/vi/docs/developer-api/validation' },
      { text: 'Ví dụ', link: '/vi/docs/developer-api/examples' }
    ]
  }
]

const enSidebar = {
  '/docs/developer-api/': enDeveloperSidebar,
  '/docs/changelog': [{ text: 'Changelog', items: [{ text: 'Release History', link: '/docs/changelog' }] }],
  '/docs/': enManualSidebar
}

const viSidebar = {
  '/vi/docs/developer-api/': viDeveloperSidebar,
  '/vi/docs/changelog': [{ text: 'Nhật ký thay đổi', items: [{ text: 'Lịch sử phát hành', link: '/vi/docs/changelog' }] }],
  '/vi/docs/': viManualSidebar
}

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
          { text: 'Docs', link: '/docs/', activeMatch: '^/docs/(?!developer-api|changelog)' },
          { text: 'Developer API', link: '/docs/developer-api/', activeMatch: '^/docs/developer-api' },
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
          { text: 'Tài liệu', link: '/vi/docs/', activeMatch: '^/vi/docs/(?!developer-api|changelog)' },
          { text: 'API lập trình', link: '/vi/docs/developer-api/', activeMatch: '^/vi/docs/developer-api' },
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
