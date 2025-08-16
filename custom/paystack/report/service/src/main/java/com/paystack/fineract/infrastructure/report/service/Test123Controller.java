/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.paystack.fineract.infrastructure.report.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Path("/v1/custom-report")
@Component
public class Test123Controller {

    @GET
    @Path("/str")
    public String sayHelloStr(){
        return "Hello custom report";
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public List<TestTestObj> sayHello(){
        TestTestObj obj = new TestTestObj("Hello", "Report");
        List<TestTestObj> list = new ArrayList<>();
        list.add(obj);
        return list;
    }

    class TestTestObj {
        String key;
        String value;

        public TestTestObj(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
