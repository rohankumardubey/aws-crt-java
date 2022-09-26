/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.awssdk.crt.auth.credentials;

/**
 * Interface that synchronously provides custom credentials.
 */
public interface DelegateCredentialsHandler {

    /**
     * Called from Native when delegate credential provider needs to fetch a
     * credential.
     * @return Credentials
     */
    Credentials getCredentials();
}
