/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting2.errors;

import java.util.UUID;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;

public class DefaultServiceException extends ServiceException {

    private static final Response.StatusType DEFAULT_STATUS = Response.Status.INTERNAL_SERVER_ERROR;

    private final ServiceExceptionLogger messageLogger;
    private final Response.StatusType status;
    private final String errorId = UUID.randomUUID().toString();

    public DefaultServiceException(String messageFormat, Param<?>... messageArgs) {
        this(messageFormat, DEFAULT_STATUS, messageArgs);
    }

    public DefaultServiceException(String messageFormat, Throwable cause, Param<?>... messageArgs) {
        this(messageFormat, DEFAULT_STATUS, cause, messageArgs);
    }

    public DefaultServiceException(String messageFormat, Response.StatusType status, Param<?>... messageArgs) {
        this(messageFormat, status, null, messageArgs);
    }

    public DefaultServiceException(String messageFormat, Response.StatusType status, @Nullable Throwable cause,
            Param<?>... messageArgs) {
        super(ServiceExceptionLogger.format(messageFormat, messageArgs), cause);

        this.status = status;
        this.messageLogger = new ServiceExceptionLogger(messageFormat, messageArgs, this);
    }

    @Override
    public final void logTo(Logger log) {
        messageLogger.logTo(log);
    }

    /**
     * Subclasses may override this method to return custom errors to the remote caller.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Override
    public final SerializableError getError() {
        return SerializableError.of(
                "Refer to the server logs with this errorId: " + errorId,
                this.getClass());
    }

    @Override
    public final Response.StatusType getStatus() {
        return status;
    }

    @Override
    public final String getErrorId() {
        return errorId;
    }

}
