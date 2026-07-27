package github.nighter.smartspawner;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Supplies the SQLite JDBC driver to the plugin classloader.
 *
 * <p>SQLite is the default storage backend, so the driver has to be present rather than assumed.
 * It is resolved here instead of being shaded into the jar because the xerial driver extracts a
 * native library from a resource path derived from its own package name, which relocation breaks.
 * Paper caches the download under {@code libraries/} after the first startup.</p>
 *
 * <p>The MariaDB driver is not listed here: it is shaded and relocated into the plugin jar, which
 * is safe because it is pure Java.</p>
 */
public class SmartSpawnerLoader implements PluginLoader {

    private static final String SQLITE_DRIVER = "org.xerial:sqlite-jdbc:3.53.2.1";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Paper rejects pointing straight at repo1.maven.org: using Maven Central as a CDN breaks
        // its terms of service. MAVEN_CENTRAL_DEFAULT_MIRROR is the mirror URL it wants instead.
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        resolver.addDependency(new Dependency(new DefaultArtifact(SQLITE_DRIVER), null));
        classpathBuilder.addLibrary(resolver);
    }
}
