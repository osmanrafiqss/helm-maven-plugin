package com.kiwigrid.helm.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.Charset;

import com.kiwigrid.helm.maven.plugin.exception.BadUploadException;
import com.kiwigrid.helm.maven.plugin.pojo.HelmRepository;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for uploading to helm repo (e.g. chartmuseum)
 *
 * @author Fabian Schlegel
 * @since 02.01.18
 */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
public class UploadMojo extends AbstractHelmMojo {

	@Parameter(property = "helm.upload.skip", defaultValue = "false")
	private boolean skipUpload;

	public void execute()
			throws MojoExecutionException
	{
		if (skip || skipUpload) {
			getLog().info("Skip upload");
			return;
		}
		getLog().info("Uploading to " + getHelmUploadUrl() + "\n");
		for (String chartPackageFile : getChartTgzs(getOutputDirectory())) {
			getLog().info("Uploading " + chartPackageFile + "...");
			try {
				uploadSingle(chartPackageFile);
			} catch (BadUploadException | IOException e) {
				getLog().error(e.getMessage());
				throw new MojoExecutionException("Error uploading " + chartPackageFile + " to " + getHelmUploadUrl(),
						e);
			}
		}
	}

	private void uploadSingle(String file) throws IOException, BadUploadException, MojoExecutionException {
		final File fileToUpload = new File(file);
		final HelmRepository uploadRepo = getHelmUploadRepo();

		HttpURLConnection connection;

		if(uploadRepo.getType() == null){
			throw new IllegalArgumentException("Repository type missing. Check your plugin configuration.");
		}

		switch (uploadRepo.getType()) {
		case ARTIFACTORY:
			connection = getConnectionForUploadToArtifactory(fileToUpload);
			break;
		case CHARTMUSEUM:
			connection = getConnectionForUploadToChartmuseum();
			break;
		default:
			throw new IllegalArgumentException("Unsupported repository type for upload.");
		}

		try (FileInputStream fileInputStream = new FileInputStream(fileToUpload)) {
			IOUtils.copy(fileInputStream, connection.getOutputStream());
		}
		if (connection.getResponseCode() >= 300) {
			String response = IOUtils.toString(connection.getErrorStream(), Charset.defaultCharset());
			throw new BadUploadException(response);
		} else {
			String response = IOUtils.toString(connection.getInputStream(), Charset.defaultCharset());
			getLog().info(Integer.toString(connection.getResponseCode()) + " - " + response);
		}
		connection.disconnect();
	}

	protected HttpURLConnection getConnectionForUploadToChartmuseum() throws IOException {
		final HttpURLConnection connection = (HttpURLConnection) new URL(getHelmUploadUrl()).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/gzip");

		return connection;
	}

	protected HttpURLConnection getConnectionForUploadToArtifactory(File file) throws IOException, MojoExecutionException {
		String uploadUrl = getHelmUploadUrl();
		// Append slash if not already in place
		if (!uploadUrl.endsWith("/")) {
			uploadUrl += "/";
		}
		uploadUrl = uploadUrl + file.getName();

		final HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("PUT");
		connection.setRequestProperty("Content-Type", "application/gzip");

		verifyAndSetAuthentication();

		return connection;
	}

	private void verifyAndSetAuthentication() throws MojoExecutionException {

		PasswordAuthentication authentication = getAuthentication(getHelmUploadRepo());
		if (authentication == null) {
			throw new IllegalArgumentException("Credentials has to be configured for uploading to Artifactory.");
		}

		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return authentication;
			}
		});
	}
}
