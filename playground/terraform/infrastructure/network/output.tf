#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

output "playground_network_id" {
  value = google_compute_network.playground.id
}

output "playground_subnetwork_id" {
  value = google_compute_subnetwork.playground.id
}

output "playground_network_name" {
  value = google_compute_network.playground.name
}

output "playground_subnetwork_name" {
  value = google_compute_subnetwork.playground.name
}

output "playground_subnetwork_cidr" {
  value = google_compute_subnetwork.playground.ip_cidr_range
}