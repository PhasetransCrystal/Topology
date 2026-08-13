import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Topology Wiki',
  tagline: 'Documentation for the Topology Minecraft Mod',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://topology.ptcrys.net',
  baseUrl: process.env.DOCUSAURUS_BASE_URL || '/',

  organizationName: 'PhasetransCrystal',
  projectName: 'Topology',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
  },
  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs-content',
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/PhasetransCrystal/Topology/tree/master/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Topology',
      logo: {
        alt: 'Topology Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'https://ptcrys.net',
          label: 'Ptcrys',
          position: 'right',
        },
        {
          href: 'https://maven.ptcrys.net',
          label: 'Maven',
          position: 'right',
        },
        {
          href: 'https://github.com/PhasetransCrystal/Topology',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Introduction',
              to: '/docs',
            },
          ],
        },
        {
          title: 'Links',
          items: [
            {
              label: 'Ptcrys',
              href: 'https://ptcrys.net',
            },
            {
              label: 'Maven',
              href: 'https://maven.ptcrys.net',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/PhasetransCrystal/Topology',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} PhasetransCrystal. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;