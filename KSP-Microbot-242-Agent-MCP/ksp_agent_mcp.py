#!/usr/bin/env python3
"""Expose KSP Microbot's local Agent Server observation tools over MCP stdio."""

import argparse
import http.client
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlencode

PROTOCOL_VERSIONS = ("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
MAX_RESPONSE_BYTES = 1024 * 1024


def integer(minimum, maximum, default=None):
    schema = {"type": "integer", "minimum": minimum, "maximum": maximum}
    if default is not None:
        schema["default"] = default
    return schema


TEXT = {"type": "string", "minLength": 1, "maxLength": 120}
ID = integer(0, 2147483647)
LIMIT = integer(1, 200, 40)
NEARBY = {"nameContains": TEXT, "id": ID, "maxDistance": integer(1, 104, 20), "limit": LIMIT}


def tool(path, description, properties=None, required=()):
    schema = {
        "type": "object",
        "properties": properties or {},
        "additionalProperties": False,
    }
    if required:
        schema["required"] = list(required)
    return path, {"description": description, "inputSchema": schema}


TOOLS = {
    "client_health": tool("/", "Check whether Microbot's authenticated Agent Server is available."),
    "client_state": tool("/state", "Read the current game state and local player."),
    "client_skills": tool("/skills", "Read skill levels and experience."),
    "client_inventory": tool("/inventory", "Read items in the inventory."),
    "client_bank": tool("/bank", "Read bank status and available bank contents."),
    "client_dialogue": tool("/dialogue", "Read current dialogue text and options."),
    "client_npcs": tool("/npcs", "Find nearby NPCs and read their locations and actions.", NEARBY),
    "client_objects": tool("/objects", "Find nearby game objects and read their locations and actions.", NEARBY),
    "client_widgets": tool(
        "/widgets/list", "List visible widget groups with pagination.",
        {"limit": LIMIT, "offset": integer(0, 100000, 0)},
    ),
    "client_widget_search": tool(
        "/widgets/search", "Find widgets by text or keyword.",
        {"q": TEXT, "limit": LIMIT}, ("q",),
    ),
    "client_widget": tool(
        "/widgets/describe", "Inspect a widget and its descendants.",
        {"groupId": integer(0, 65535), "childId": integer(0, 65535), "depth": integer(0, 5, 2)},
        ("groupId", "childId"),
    ),
    "client_varp": tool("/varp", "Read a player variable by ID. Tutorial Island progress is varp 281.", {"id": ID}, ("id",)),
    "client_varbit": tool("/varbit", "Read a client varbit by ID.", {"id": ID}, ("id",)),
}


class BridgeError(Exception):
    """An error that can be reported to the MCP caller."""


def validate_arguments(arguments, schema):
    if not isinstance(arguments, dict):
        raise BridgeError("Tool arguments must be an object.")
    properties = schema["properties"]
    if arguments.keys() - properties.keys():
        raise BridgeError("Unknown tool argument.")
    if set(schema.get("required", ())) - arguments.keys():
        raise BridgeError("Required tool argument missing.")

    query = {}
    for name, field in properties.items():
        if name not in arguments:
            if "default" in field:
                query[name] = field["default"]
            continue
        value = arguments[name]
        if field["type"] == "integer":
            valid = type(value) is int and field["minimum"] <= value <= field["maximum"]
        else:
            valid = isinstance(value, str) and value.strip() and len(value) <= field["maxLength"]
        if not valid:
            raise BridgeError(f"Invalid value for {name}.")
        query[name] = value
    return query


class AgentServer:
    def __init__(self, port, token_file):
        self.port = port
        self.token_file = token_file

    def token(self):
        try:
            token = self.token_file.read_text(encoding="utf-8-sig").strip()
        except (OSError, UnicodeError) as error:
            raise BridgeError(
                f"Cannot read {self.token_file}. Enable Agent Server for this client or pass --token-file."
            ) from error
        if not token or any(not 33 <= ord(character) <= 126 for character in token):
            raise BridgeError("The Agent Server token file contains an invalid token.")
        return token

    def get(self, path, query):
        token = self.token()
        target = f"{path}?{urlencode(query)}" if query else path
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=8)
        try:
            connection.request("GET", target, headers={"X-Agent-Token": token, "Accept": "application/json"})
            with connection.getresponse() as response:
                if response.status in (401, 403):
                    raise BridgeError("Agent Server rejected the token. Check that the token file belongs to the running client.")
                if response.status != 200:
                    raise BridgeError(f"Agent Server returned HTTP {response.status} for {path}.")
                body = response.read(MAX_RESPONSE_BYTES + 1)
        except (OSError, http.client.HTTPException) as error:
            raise BridgeError(
                f"Agent Server is unavailable at 127.0.0.1:{self.port}. Check the client, TCP port and Stealth bind setting."
            ) from error
        finally:
            connection.close()

        if len(body) > MAX_RESPONSE_BYTES:
            raise BridgeError("Agent Server response is too large. Narrow the query or lower its limit.")
        try:
            return json.loads(body)
        except (ValueError, RecursionError) as error:
            raise BridgeError("Agent Server returned invalid JSON.") from error

    def health(self):
        catalog = self.get("/", {})
        if not isinstance(catalog, dict) or not isinstance(catalog.get("endpoints"), list):
            raise BridgeError("The listener did not return a Microbot Agent Server endpoint catalog.")
        return {"connected": True, "host": "127.0.0.1", "port": self.port}


def tool_result(value, is_error=False):
    return {"content": [{"type": "text", "text": value}], "isError": is_error}


def call_tool(agent, params):
    name = params.get("name")
    if not isinstance(name, str) or name not in TOOLS:
        return tool_result("Unknown MCP tool.", is_error=True)
    path, definition = TOOLS[name]
    try:
        query = validate_arguments(params.get("arguments", {}), definition["inputSchema"])
        result = agent.health() if name == "client_health" else agent.get(path, query)
        return tool_result(json.dumps(result, ensure_ascii=True))
    except BridgeError as error:
        return tool_result(str(error), is_error=True)


def rpc_error(request_id, code, message):
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def dispatch(agent, request):
    if not isinstance(request, dict) or request.get("jsonrpc") != "2.0" or not isinstance(request.get("method"), str):
        return rpc_error(None, -32600, "Invalid request")
    if "id" not in request:
        return None
    request_id = request["id"]
    if request_id is None or type(request_id) not in (str, int):
        return rpc_error(None, -32600, "Invalid request ID")

    method = request["method"]
    params = request.get("params", {})
    if not isinstance(params, dict):
        return rpc_error(request_id, -32602, "Invalid params")
    if method == "initialize":
        requested_version = params.get("protocolVersion")
        if not isinstance(requested_version, str):
            return rpc_error(request_id, -32602, "Missing protocolVersion")
        version = requested_version if requested_version in PROTOCOL_VERSIONS else PROTOCOL_VERSIONS[0]
        result = {
            "protocolVersion": version,
            "capabilities": {"tools": {}, "resources": {}},
            "serverInfo": {"name": "ksp-microbot-agent", "version": "1.1.0"},
        }
    elif method == "ping":
        result = {}
    elif method == "tools/list":
        result = {"tools": [{"name": name, **definition} for name, (_, definition) in TOOLS.items()]}
    elif method == "resources/list":
        result = {"resources": []}
    elif method == "resources/templates/list":
        result = {"resourceTemplates": []}
    elif method == "tools/call":
        result = call_tool(agent, params)
    else:
        return rpc_error(request_id, -32601, "Method not found")
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def serve(agent):
    for line in sys.stdin.buffer:
        try:
            request = json.loads(line.decode("utf-8"))
        except (ValueError, RecursionError):
            response = rpc_error(None, -32700, "Parse error")
        else:
            response = dispatch(agent, request)
        if response is not None:
            sys.stdout.buffer.write(json.dumps(response, ensure_ascii=True).encode("ascii") + b"\n")
            sys.stdout.buffer.flush()


def main():
    profile = Path(os.environ.get("USERPROFILE", str(Path.home())))
    default_token = profile / "Downloads" / "KSP-Microbot-242-Test" / "local-login" / "profile" / ".runelite" / ".agent-token"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=os.environ.get("MICROBOT_PORT"))
    parser.add_argument("--token-file", type=Path, default=os.environ.get("MICROBOT_TOKEN_FILE", str(default_token)))
    parser.add_argument("--check", action="store_true", help="Check connectivity and exit instead of serving MCP")
    args = parser.parse_args()
    if args.port is None:
        config_file = Path(__file__).with_suffix(".port")
        try:
            args.port = int(config_file.read_text(encoding="utf-8-sig").strip())
        except FileNotFoundError:
            args.port = 8081
        except (OSError, ValueError):
            parser.error(f"{config_file} must contain the Agent Server port as a number")
    if not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")

    agent = AgentServer(args.port, args.token_file)
    if args.check:
        try:
            agent.health()
        except BridgeError as error:
            print(str(error), file=sys.stderr)
            return 1
        print(f"Connected to Microbot Agent Server at 127.0.0.1:{args.port}.")
        return 0
    try:
        serve(agent)
    except BrokenPipeError:
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
