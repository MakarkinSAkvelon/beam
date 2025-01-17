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

resource "google_compute_network" "playground" {
  project = var.project_id
  name    = var.network_name
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "playground" {
  ip_cidr_range            = var.subnetwork_cidr_range
  name                     = var.subnetwork_name
  network                  = google_compute_network.playground.id
  region                   = var.region
  project                  = var.project_id
  private_ip_google_access = true
}

resource "google_compute_router" "playground-router" {
  name    = "playground-router"
  region  = google_compute_subnetwork.playground.region
  network = google_compute_network.playground.id
}

resource "google_compute_router_nat" "playground_nat_gateway" {
  name               = "playground-nat-gateway"
  router             = google_compute_router.playground-router.name
  nat_ip_allocate_option = "AUTO_ONLY"

  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  # Configure NAT for the private subnet
}

# Create a firewall rule to allow traffic from the private subnet to the NAT gateway
resource "google_compute_firewall" "private_to_nat" {
  name    = "private-to-nat"
  network = google_compute_network.playground.self_link

  allow {
    protocol = "tcp"
    ports     = ["80", "8080", "443", "22"]
  }
  target_tags = ["nat-gateway"]
  source_ranges = [google_compute_subnetwork.playground.ip_cidr_range]
}