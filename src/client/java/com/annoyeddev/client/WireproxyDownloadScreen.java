package com.annoyeddev.client;

import com.annoyeddev.backend.wireguard.WireproxyBinaryLocator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WireproxyDownloadScreen extends Screen {
	private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	private enum State {
		DOWNLOADING,
		ERROR
	}

	private final Screen parent;
	private final Path binDir;

	private State state = State.DOWNLOADING;
	private volatile long bytesDownloaded = 0;
	private volatile long totalBytes = -1;
	private volatile String errorMessage = "";
	private Future<?> downloadFuture;
	private Button retryButton;

	public WireproxyDownloadScreen(Screen parent, Path binDir) {
		super(Component.translatable("wirecraft.download.title"));
		this.parent = parent;
		this.binDir = binDir;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("wirecraft.download.cancel"), b -> cancel())
				.pos(width / 2 - 75, height / 2 + 40)
				.size(150, 20)
				.build());
		retryButton = addRenderableWidget(Button.builder(Component.translatable("wirecraft.download.retry"), b -> startDownload())
				.pos(width / 2 - 75, height / 2 + 65)
				.size(150, 20)
				.build());
		retryButton.visible = state == State.ERROR;

		if (downloadFuture == null) {
			startDownload();
		}
	}

	private void startDownload() {
		state = State.DOWNLOADING;
		bytesDownloaded = 0;
		totalBytes = -1;
		if (retryButton != null) {
			retryButton.visible = false;
		}

		downloadFuture = EXECUTOR.submit(() -> {
			try {
				WireproxyBinaryLocator.downloadFresh(binDir, (downloaded, total) -> {
					bytesDownloaded = downloaded;
					totalBytes = total;
				});
				minecraft.execute(() -> minecraft.setScreenAndShow(parent));
			} catch (IOException e) {
				errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
				minecraft.execute(() -> {
					state = State.ERROR;
					if (retryButton != null) {
						retryButton.visible = true;
					}
				});
			}
		});
	}

	private void cancel() {
		if (downloadFuture != null) {
			downloadFuture.cancel(true);
		}
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(context, mouseX, mouseY, partialTick);

		String titleText = title.getString();
		context.text(font, titleText, width / 2 - font.width(titleText) / 2, height / 2 - 50, 0xFFFFFFFF, true);

		if (state == State.DOWNLOADING) {
			int barWidth = 200;
			int barHeight = 14;
			int x = width / 2 - barWidth / 2;
			int y = height / 2 - 10;

			context.fill(x, y, x + barWidth, y + barHeight, 0xFF404040);
			long total = totalBytes;
			long downloaded = bytesDownloaded;
			String label;
			if (total > 0) {
				int filled = (int) (barWidth * Math.min(1.0, (double) downloaded / total));
				context.fill(x, y, x + filled, y + barHeight, 0xFF55AA55);
				label = Component.translatable("wirecraft.download.progressKnown",
						downloaded * 100 / total, formatBytes(downloaded), formatBytes(total)).getString();
			} else {
				label = Component.translatable("wirecraft.download.progressUnknown", formatBytes(downloaded)).getString();
			}
			context.text(font, label, width / 2 - font.width(label) / 2, y + 3, 0xFFFFFFFF, true);
		} else {
			int y = height / 2 - 10;
			context.textWithWordWrap(font, Component.translatable("wirecraft.download.failed", errorMessage), width / 2 - 150, y, 300, 0xFFFF5555);
		}
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}
}
