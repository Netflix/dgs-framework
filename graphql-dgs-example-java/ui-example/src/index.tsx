import React, {useState} from 'react';
import ReactDOM from 'react-dom';
import {
    ApolloClient,
    NormalizedCacheObject,
    ApolloProvider,
    gql,
    useQuery, InMemoryCache, split, createHttpLink, useSubscription
} from '@apollo/client';

import {WebSocketLink} from "@apollo/client/link/ws";

const httpLink = createHttpLink({uri:'http://localhost:8080/graphql' })

const webSocketLink = new WebSocketLink({
    uri: 'ws://localhost:8080/subscriptions'
});

const client: ApolloClient<NormalizedCacheObject> = new ApolloClient({
    link: split((operation) => {
        return operation.operationName === "StockWatch"
    }, webSocketLink, httpLink),
    cache: new InMemoryCache(),
    headers: {},
    resolvers: {},
});

const App: React.FC = () => {
    const {loading, error, data} = useQuery(gql`
        {
            movies {
                title
                director
                __typename
                ...on ActionMovie {
                    nrOfExplosions
                }
                ...on ScaryMovie {
                    gory
                }
            }
        }`);

    return loading ?
        <div>Loading...</div>
        : error ? <div>{error}</div>
            : <div>
                <h1>Movies</h1>
                <table>
                    <tr>
                        <th>Title</th>
                        <th>Director</th>
                        <th>Type</th>
                        <th>Explosions</th>
                        <th>Gory</th>
                    </tr>
                    {data.movies.map((movie: Movie) => {
                        return <tr>
                            <td>{movie.title}</td>
                            <td>{movie.director}</td>
                            <td>{movie.__typename}</td>
                            <td>{movie.nrOfExplosions}</td>
                            <td>{movie.gory?"yes":"no"}</td>
                        </tr>
                    })}
                </table>

                <h1>Subscriptions</h1>
                <SubscriptionPanel/>
            </div>
}

type Movie = {
    title: String
    director: String
    __typename: String
    nrOfExplosions: Number
    gory: Boolean
}

const SubscriptionPanel : React.FC = () => {
    const {data, loading, error} = useSubscription(gql`
        subscription StockWatch {
            stocks {
                name
                price
            }
        }
    `, {});


    return data?<div>{data.stocks.name}: { data.stocks.price}</div>:<div/>
}

ReactDOM.render(
    <ApolloProvider client={client}>
        <App/>
    </ApolloProvider>,
    document.getElementById('root'),
);