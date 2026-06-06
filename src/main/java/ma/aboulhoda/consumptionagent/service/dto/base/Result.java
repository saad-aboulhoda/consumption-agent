package ma.aboulhoda.consumptionagent.service.dto.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private boolean flag;
    private int code;
    private String message;
    private T data;

    public Result(boolean flag, int code, String message) {
        this.flag = flag;
        this.code = code;
        this.message = message;
    }

    public static Result<?> success() {
        return new Result<>(true, StatusCode.OK, "Success");
    }

    public static Result<?> created() {
        return new Result<>(true, StatusCode.CREATED, "Created");
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, StatusCode.OK, "Success", data);
    }

    public static <T> Result<T> created(T data) {
        return new Result<>(true, StatusCode.CREATED, "Created", data);
    }
}