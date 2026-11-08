import mongoose from "mongoose";

const connectDB = async () => {
    try {
        const mongoURI = process.env.MONGODB_URI;

        if (!mongoURI) {
            throw new Error(
                "MONGODB_URI is missing from environment variables."
            );
        }

        // Reuse an existing connection
        if (mongoose.connection.readyState === 1) {
            return mongoose.connection;
        }

        // Wait if a connection is already being established
        if (mongoose.connection.readyState === 2) {
            return mongoose.connection.asPromise();
        }

        const connection = await mongoose.connect(mongoURI, {
            serverSelectionTimeoutMS: 10000,
            maxPoolSize: 10,
            minPoolSize: 0,
            maxIdleTimeMS: 30000,
            bufferCommands: false
        });

        console.log("------------------------------------");
        console.log("MongoDB Connected Successfully");
        console.log(`Database: ${connection.connection.name}`);
        console.log(`Host: ${connection.connection.host}`);
        console.log("------------------------------------");

        return connection;

    } catch (error) {
        console.error(
            "MongoDB Connection Failed:",
            error.message
        );

        throw error;
    }
};

export default connectDB;