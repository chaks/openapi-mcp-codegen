import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';
import logoPng from '@site/static/img/logo.png';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '2rem'}}>
          <div style={{flex: 1, textAlign: 'center'}}>
            <Heading as="h1" className="hero__title">
              {siteConfig.title}
            </Heading>
            <p className="hero__subtitle">{siteConfig.tagline}</p>
            <div className={styles.buttons}>
              <Link
                className="button button--secondary button--lg"
                to="/docs/intro">
                Get Started
              </Link>
              <Link
                className="button button--secondary button--lg"
                to="https://github.com/chaks/openapi-mcp-codegen">
                View on GitHub
              </Link>
            </div>
          </div>
          <img src={logoPng} alt="OpenAPI MCP Codegen Logo" className={styles.logo} />
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Generate type-safe MCP tools from OpenAPI specifications for AI agent integration">
      <HomepageHeader />
      <main>
        <section className={styles.features}>
          <div className="container">
            <div className="row">
              <div className="col col--4">
                <div className="text--center padding-horiz--md">
                  <h3>Type-Safe Generation</h3>
                  <p>
                    Generate type-safe Kotlin code using KotlinPoet from OpenAPI 3.0/3.1 specifications
                  </p>
                </div>
              </div>
              <div className="col col--4">
                <div className="text--center padding-horiz--md">
                  <h3>MCP Integration</h3>
                  <p>
                    Automatically generate Quarkus MCP tools with @Tool and @ToolArg annotations for AI/LLM integration
                  </p>
                </div>
              </div>
              <div className="col col--4">
                <div className="text--center padding-horiz--md">
                  <h3>Ready to Use</h3>
                  <p>
                    Includes Gradle wrapper and build configuration in generated output with optional auto-compilation
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className={styles.installation}>
          <div className="container">
            <div className="row">
              <div className="col">
                <Heading as="h2">Quick Start</Heading>
                <p>Generate MCP tools from any OpenAPI specification in seconds:</p>
                <div className={styles.codeBlock}>
                  <code>
                    java -jar openapi-mcp-codegen.jar \<br/>
                    &nbsp;&nbsp;--input petstore.yaml \<br/>
                    &nbsp;&nbsp;--output ./generated \<br/>
                    &nbsp;&nbsp;--root-package io.swagger.petstore
                  </code>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
