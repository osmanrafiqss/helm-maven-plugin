package com.kiwigrid.helm.maven.plugin;

import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

/**
 * Mojo for building chart dependencies
 *
 * @author Axel Köhler
 * @since 1.1
 */
@Mojo(name = "dependency-build", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class DependencyBuildMojo extends AbstractHelmMojo {

	@Parameter(property = "helm.dependency-build.skip", defaultValue = "false")
	private boolean skipDependencyBuild;

	public void execute()
			throws MojoExecutionException
	{
		if (skip || skipDependencyBuild) {
			getLog().info("Skip dependency build");
			return;
		}
		for (String inputDirectory : getChartDirectories(getChartDirectory())) {
			getLog().info("Build chart dependencies for " + inputDirectory + "...");
			callCli(getHelmExecuteablePath()
					+ " dependency build "
					+ inputDirectory
					+ (StringUtils.isNotEmpty(getHelmHomeDirectory()) ? " --home=" + getHelmHomeDirectory() : ""),
					"Failed to resolve dependencies", true);
		}
	}
}
