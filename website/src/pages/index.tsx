import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import {JSX} from "react";

export default function Home(): JSX.Element {
  return (
    <Layout title="Topology Wiki" description="NeoForge infrastructure library for Minecraft mods">
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: 'calc(100vh - var(--ifm-navbar-height))',
          padding: '2rem',
          textAlign: 'center',
        }}
      >
        <h1 style={{ fontSize: 'clamp(2.4rem, 6vw, 3.6rem)', fontWeight: 800, marginBottom: '0.75rem', letterSpacing: '-0.04em' }}>
          Topology
        </h1>
        <p style={{ fontSize: '1.15rem', color: 'var(--ifm-color-emphasis-600)', maxWidth: 540, marginBottom: '2.5rem', lineHeight: 1.7 }}>
          NeoForge 基础库 Mod — 提供机器、材料、管道、配方、矿石生成等基础设施。<br />
          精简样板代码，你只需声明<strong>做什么</strong>，框架负责<strong>怎么做</strong>。
        </p>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', justifyContent: 'center' }}>
          <Link
            className="button button--primary button--lg"
            to="/docs"
            style={{ borderRadius: '8px', fontSize: '1rem', padding: '0.65rem 1.8rem' }}
          >
            Get Started →
          </Link>
          <Link
            className="button button--secondary button--lg"
            href="https://github.com/PhasetransCrystal/Topology"
            style={{ borderRadius: '8px', fontSize: '1rem', padding: '0.65rem 1.8rem' }}
          >
            GitHub
          </Link>
        </div>

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
          gap: '1.5rem 3rem',
          marginTop: '4rem',
          width: '100%',
          maxWidth: 560,
        }}>
          {[
            ['Machine', '/docs/machine/'],
            ['Recipe', '/docs/recipe/'],
            ['Pipe', '/docs/pipe/'],
            ['Material', '/docs/material/'],
            ['Equipment', '/docs/equipment/'],
            ['Ore', '/docs/ore/'],
          ].map(([label, to]) => (
            <Link
              key={label}
              to={to}
              style={{
                color: 'var(--ifm-color-emphasis-600)',
                fontWeight: 600,
                fontSize: '0.9rem',
                textDecoration: 'none',
                padding: '0.35rem 0',
              }}
            >
              {label}
            </Link>
          ))}
        </div>
      </div>
    </Layout>
  );
}