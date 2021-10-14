/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const {createServer} = require('http');
const {SubscriptionServer} = require('subscriptions-transport-ws');
const {execute, subscribe} = require('graphql');
const express = require("express");
const {ApolloServer, gql} = require("apollo-server-express");
const {PubSub} = require("graphql-subscriptions");
const {makeExecutableSchema} = require("@graphql-tools/schema");

(async () => {
    const PORT = 4000;
    const HOST = '0.0.0.0';
    const pubsub = new PubSub();
    const app = express();
    const httpServer = createServer(app);

    // Schema definition
    const typeDefs = gql`
        type Query {
            hello(name: String): String
        }
        type Subscription {
            stocks: Stock
        }

        type Stock {
            name: String
            price: Float
        }
    `;

    // Resolver map
    const resolvers = {
        Query: {
            hello: () => '',
        },
        Subscription: {
            stocks: {
                subscribe: (() => {
                    const channel = Math.random().toString(36).substring(2, 15); // random channel name
                    let price = 500.5;
                    setInterval(() => pubsub.publish(channel, {stocks: {name: 'NFLX', price: price++}}), 1000)
                    return pubsub.asyncIterator(channel);
                }),
            },
        },
    };

    const schema = makeExecutableSchema({typeDefs, resolvers});

    const server = new ApolloServer({
        schema,
    });
    await server.start();
    server.applyMiddleware({app});

    SubscriptionServer.create(
        {schema, execute, subscribe},
        {server: httpServer, path: server.graphqlPath}
    );

    httpServer.listen(PORT, HOST,() => {
        console.log(
            `🚀 Query endpoint ready at http://${HOST}:${PORT}${server.graphqlPath}`
        );
        console.log(
            `🚀 Subscription endpoint ready at ws://${HOST}:${PORT}${server.graphqlPath}`
        );
    });
})();



