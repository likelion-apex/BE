package domain.beauty.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class BeautyRoutineException extends RuntimeException {

	protected BeautyRoutineException(String message) {
		super(message);
	}

	protected BeautyRoutineException(String message, Throwable cause) {
		super(message, cause);
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public static final class InvalidYouTubeUrl extends BeautyRoutineException {
		public InvalidYouTubeUrl(String message) {
			super(message);
		}
	}

	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public static final class GeminiUnavailable extends BeautyRoutineException {
		public GeminiUnavailable(String message) {
			super(message);
		}

		public GeminiUnavailable(String message, Throwable cause) {
			super(message, cause);
		}
	}

	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public static final class InvalidGeminiResponse extends BeautyRoutineException {
		public InvalidGeminiResponse(String message) {
			super(message);
		}

		public InvalidGeminiResponse(String message, Throwable cause) {
			super(message, cause);
		}
	}

	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public static final class MissingGeminiConfiguration extends BeautyRoutineException {
		public MissingGeminiConfiguration(String message) {
			super(message);
		}
	}
}
