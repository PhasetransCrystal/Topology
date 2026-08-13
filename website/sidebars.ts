import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'intro',
    'getting-started',
    'embedding',
    {
      type: 'category',
      label: 'Recipe',
      link: {
        type: 'doc',
        id: 'recipe',
      },
      items: ['recipe/recipe-capability', 'recipe/recipe-type'],
    },
    {
      type: 'category',
      label: 'Material',
      link: {
        type: 'doc',
        id: 'material',
      },
      items: [
        'material/material-form',
        'material/material-data',
        'material/material-post-processor',
      ],
    },
    {
      type: 'category',
      label: 'Equipment',
      link: {
        type: 'doc',
        id: 'equipment',
      },
      items: ['equipment/equipment-behavior', 'equipment/equipment-tooltips'],
    },
    {
      type: 'category',
      label: 'Machine',
      link: {
        type: 'doc',
        id: 'machine',
      },
      items: [
        'machine/machine-component',
        'machine/machine-render',
        'machine/machine-resource',
        'machine/multiblock-ability',
        'machine/multiblock',
        'machine/machine-ui',
      ],
    },
    {
      type: 'category',
      label: 'Pipe',
      link: {
        type: 'doc',
        id: 'pipe',
      },
      items: ['pipe/pipe-strategy', 'pipe/pipe-survey'],
    },
    {
      type: 'category',
      label: 'Ore',
      link: {
        type: 'doc',
        id: 'ore',
      },
      items: ['ore/ore-strategies', 'ore/ore-display'],
    },
    {
      type: 'category',
      label: 'Localization',
      link: {
        type: 'doc',
        id: 'localization',
      },
      items: ['localization/keys-and-families', 'localization/display-names'],
    },
    {
      type: 'category',
      label: 'Foundation',
      link: {
        type: 'generated-index',
      },
      items: [
        'foundation/lifecycle',
        'foundation/extension-points',
        'foundation/tick-system',
        'foundation/tooltip-panels',
        'foundation/connected-textures',
        'foundation/helpers',
        'foundation/ui-instrumentation',
      ],
    },
  ],
};

export default sidebars;
