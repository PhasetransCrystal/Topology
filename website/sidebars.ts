import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'intro',
    'getting-started',
    {
      type: 'category',
      label: 'Materials & World',
      items: ['materials-world/material', 'materials-world/ore', 'materials-world/equipment'],
    },
    {
      type: 'category',
      label: 'Automation',
      items: ['automation/machine', 'automation/multiblock-patterns', 'automation/recipe', 'automation/pipe'],
    },
    {
      type: 'category',
      label: 'Foundation',
      items: ['foundation/localization', 'foundation/tick-system'],
    },
  ],
};

export default sidebars;
